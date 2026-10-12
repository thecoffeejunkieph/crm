package ph.thecoffeejunkie.crm.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Admin-side user management. Callers enforce ADMIN; BootstrapAdmin calls invite with no login at all. */
@Service
class UserAdminService {

    private static final String ENTITY = "USER";
    private static final Set<String> SORTABLE = Set.of("email", "firstName", "lastName", "role", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("email");

    private final AppUserRepository users;
    private final OneTimeTokens tokens;
    private final Warehouses warehouses;
    private final SessionAdmin sessions;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final ApplicationEventPublisher events;
    private final String frontendUrl;

    UserAdminService(AppUserRepository users, OneTimeTokens tokens, Warehouses warehouses, SessionAdmin sessions,
                     AuditLog audit, CurrentActor actor, ApplicationEventPublisher events,
                     @Value("${app.frontend-url}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.warehouses = warehouses;
        this.sessions = sessions;
        this.audit = audit;
        this.actor = actor;
        this.events = events;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    UserResponse invite(InviteUserRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Set<Long> assigned = activeWarehouses(req.warehouseIds());
        requireForRole(req.role(), assigned);
        if (users.findByEmailAndArchivedAtIsNull(email).isPresent()) {
            throw duplicate();
        }
        AppUser u = new AppUser();
        u.email = email;
        u.firstName = req.firstName().trim();
        u.lastName = req.lastName().trim();
        u.phone = req.phone();
        u.role = req.role();
        u.warehouseIds = new HashSet<>(assigned);
        u.inviteEmailStatus = "PENDING";
        try {
            users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(); // lost a race on the partial unique index
        }
        issueInvite(u);
        audit.record(AuditAction.INVITE_SENT, ENTITY, u.id, "Invited " + email, null);
        return UserResponse.of(u);
    }

    @Transactional
    UserResponse update(long id, UpdateUserRequest req) {
        AppUser u = active(id);
        if (u.version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
        Role role = req.role() == null ? u.role : req.role();
        Set<Long> assigned = req.warehouseIds() == null ? u.warehouseIds : activeWarehouses(req.warehouseIds());
        requireForRole(role, assigned);
        if (u.role == Role.ADMIN && role != Role.ADMIN) {
            requireAnotherAdmin(u);
        }

        AuditChanges profile = AuditChanges.diff()
                .field("firstName", u.firstName, req.firstName() == null ? u.firstName : req.firstName().trim())
                .field("lastName", u.lastName, req.lastName() == null ? u.lastName : req.lastName().trim())
                .field("phone", u.phone, req.phone() == null ? u.phone : req.phone());
        AuditChanges roleChange = AuditChanges.diff().field("role", u.role.name(), role.name());
        AuditChanges assignment = AuditChanges.diff().field("warehouseIds",
                u.warehouseIds.stream().sorted().toList(), assigned.stream().sorted().toList());
        if (req.firstName() != null) {
            u.firstName = req.firstName().trim();
        }
        if (req.lastName() != null) {
            u.lastName = req.lastName().trim();
        }
        if (req.phone() != null) {
            u.phone = req.phone();
        }
        u.role = role;
        u.warehouseIds = new HashSet<>(assigned);
        users.saveAndFlush(u);

        if (!profile.isEmpty()) {
            audit.record(AuditAction.UPDATED, ENTITY, id, "Updated " + u.email, profile);
        }
        if (!roleChange.isEmpty()) {
            audit.record(AuditAction.ROLE_CHANGED, ENTITY, id, "Role of " + u.email + " changed", roleChange);
        }
        if (!assignment.isEmpty()) {
            audit.record(AuditAction.ASSIGNMENT_CHANGED, ENTITY, id, "Warehouses of " + u.email + " changed",
                    assignment);
        }
        // Old sessions carry the old role and warehouses in their principal.
        if (!roleChange.isEmpty() || !assignment.isEmpty()) {
            sessions.revokeAll(principal(id));
        }
        return UserResponse.of(u);
    }

    @Transactional
    void archive(long id) {
        AppUser u = active(id);
        if (u.role == Role.ADMIN) {
            requireAnotherAdmin(u);
        }
        u.archivedAt = Instant.now();
        users.saveAndFlush(u);
        audit.record(AuditAction.ARCHIVED, ENTITY, id, "Archived " + u.email, null);
        sessions.revokeAll(principal(id));
    }

    @Transactional
    void resendInvite(long id) {
        AppUser u = active(id);
        if (u.passwordHash != null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "This user has already accepted the invite.");
        }
        u.inviteEmailStatus = "PENDING";
        users.saveAndFlush(u);
        issueInvite(u);
        audit.record(AuditAction.INVITE_SENT, ENTITY, id, "Resent invite to " + u.email, null);
    }

    @Transactional(readOnly = true)
    PageResponse<UserResponse> list(String q, Role role, boolean includeArchived, PageQuery page) {
        Specification<AppUser> spec = Specification.allOf(
                (r, cq, cb) -> includeArchived ? null : cb.isNull(r.get("archivedAt")),
                (r, cq, cb) -> role == null ? null : cb.equal(r.get("role"), role),
                matches(q));
        return PageResponse.of(users.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT)).map(UserResponse::of));
    }

    private static Specification<AppUser> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return (r, cq, cb) -> {
            var name = cb.concat(cb.concat(cb.lower(r.get("firstName")), " "), cb.lower(r.get("lastName")));
            return cb.or(cb.like(cb.lower(r.get("email")), like, '\\'), cb.like(name, like, '\\'));
        };
    }

    private void issueInvite(AppUser u) {
        Long createdBy = actor.staff().map(StaffPrincipal::userId).orElse(null);
        String token = tokens.issue(TokenPurpose.INVITE, ENTITY, u.id, Duration.ofHours(24), createdBy);
        events.publishEvent(new InviteIssued(u.id, frontendUrl + "/invite/" + token));
    }

    private AppUser active(long id) {
        return users.findById(id).filter(u -> u.archivedAt == null)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private Set<Long> activeWarehouses(Set<Long> ids) {
        Set<Long> set = ids == null ? Set.of() : ids;
        if (!set.isEmpty() && !warehouses.allActive(set)) {
            throw invalid("Unknown or archived warehouse.");
        }
        return set;
    }

    private static void requireForRole(Role role, Set<Long> assigned) {
        if (role == Role.WAREHOUSE && assigned.isEmpty()) {
            throw invalid("Warehouse staff need at least one warehouse.");
        }
    }

    /** Locks the active admins first, so two concurrent removals cannot both see the other as the survivor. */
    private void requireAnotherAdmin(AppUser leaving) {
        if (users.findByRoleAndArchivedAtIsNull(Role.ADMIN).stream().noneMatch(a -> !a.id.equals(leaving.id))) {
            throw new DomainException(ErrorCode.LAST_ADMIN, "There must be at least one active admin.");
        }
    }

    private static DomainException invalid(String detail) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, detail, Map.of("field", "warehouseIds"));
    }

    private static DomainException duplicate() {
        return new DomainException(ErrorCode.DUPLICATE, "A user with this email already exists.");
    }

    private static String principal(long id) {
        return "user:" + id;
    }
}
