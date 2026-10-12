package ph.thecoffeejunkie.crm.approvals;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class Approvals {

    private static final String ENTITY = "APPROVAL";

    private final ApprovalRequestRepository repo;
    private final AuditLog audit;
    private final Notifications notifications;
    private final Users users;
    private final CurrentActor actor;
    private final TransactionTemplate tx;

    Approvals(ApprovalRequestRepository repo, AuditLog audit, Notifications notifications, Users users,
              CurrentActor actor, PlatformTransactionManager txm) {
        this.repo = repo;
        this.audit = audit;
        this.notifications = notifications;
        this.users = users;
        this.actor = actor;
        this.tx = new TransactionTemplate(txm);
    }

    /** Call inside the guarded action's transaction. Admins pass (audited); others need an APPROVED row. */
    @Transactional
    public void ensureApproved(ApprovalType type, String subjectType, long subjectId, long subjectVersion,
                               Map<String, Object> requestedValue) {
        if (actor.staff().filter(s -> s.role() == Role.ADMIN).isPresent()) {
            // Audited under the subject so it shows in the subject's own history.
            audit.record(AuditAction.SELF_APPROVED, subjectType, subjectId, "self-approved (admin)", null);
            return;
        }
        if (repo.existsByTypeAndSubjectTypeAndSubjectIdAndSubjectVersionAndStatus(
                type, subjectType, subjectId, subjectVersion, ApprovalStatus.APPROVED)) {
            return;
        }
        throw new DomainException(ErrorCode.APPROVAL_REQUIRED, "Approval required",
                Map.of("type", type, "subjectType", subjectType, "subjectId", subjectId,
                        "subjectVersion", subjectVersion, "requestedValue", requestedValue));
    }

    /**
     * Idempotent per (type, subject, version) while PENDING. The partial unique index is the arbiter: a concurrent
     * insert that loses has its transaction replaced by a fresh one that re-reads the winning row.
     * Deliberately not @Transactional, so the failed transaction can be caught here.
     */
    public ApprovalResponse request(CreateApprovalRequest req) {
        long userId = actor.scope().userId();
        try {
            return tx.execute(s -> create(req, userId));
        } catch (DataIntegrityViolationException e) {
            return tx.execute(s -> toResponse(findPending(req).orElseThrow(() -> e)));
        }
    }

    private ApprovalResponse create(CreateApprovalRequest req, long userId) {
        Optional<ApprovalRequest> existing = findPending(req);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        ApprovalRequest a = new ApprovalRequest();
        a.type = req.type();
        a.subjectType = req.subjectType();
        a.subjectId = req.subjectId();
        a.subjectVersion = req.subjectVersion();
        a.requestedValue = req.requestedValue();
        a.reason = req.reason();
        a.requestedBy = userId;
        repo.saveAndFlush(a);
        audit.record(AuditAction.APPROVAL_REQUESTED, ENTITY, a.id,
                req.type() + " approval requested for " + req.subjectType() + " #" + req.subjectId(), null);
        notifications.toRole(Role.ADMIN, NotificationType.APPROVAL_REQUESTED, ENTITY, a.id,
                "Approval requested", req.type() + " on " + req.subjectType() + " #" + req.subjectId()
                        + ": " + req.reason());
        return toResponse(a);
    }

    @Transactional
    public ApprovalResponse decide(long id, DecisionRequest req) {
        ApprovalRequest a = repo.findById(id).orElseThrow(Approvals::notFound);
        requirePending(a);
        a.status = req.approve() ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        a.decidedBy = actor.scope().userId();
        a.decidedAt = Instant.now();
        a.decisionNote = req.note();
        repo.saveAndFlush(a);
        String outcome = a.status.name().toLowerCase();
        audit.record(AuditAction.APPROVAL_DECIDED, ENTITY, id, "approval " + outcome, null);
        notifications.toUser(a.requestedBy, NotificationType.APPROVAL_DECIDED, ENTITY, id,
                "Approval " + outcome, req.note());
        return toResponse(a);
    }

    @Transactional
    public void cancel(long id) {
        ApprovalRequest a = repo.findById(id).orElseThrow(Approvals::notFound);
        if (a.requestedBy != actor.scope().userId()) {
            throw notFound();
        }
        requirePending(a);
        a.status = ApprovalStatus.CANCELLED;
        repo.saveAndFlush(a);
        audit.record(AuditAction.APPROVAL_CANCELLED, ENTITY, id, "approval cancelled", null);
    }

    /** Admins see everything (or just their own with mine=true); everyone else only their own. */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse> list(ApprovalStatus status, boolean mine, PageQuery page) {
        Scope scope = actor.scope();
        boolean own = mine || !scope.isAdmin();
        Specification<ApprovalRequest> spec = Specification.allOf(
                (r, q, cb) -> status == null ? null : cb.equal(r.get("status"), status),
                (r, q, cb) -> own ? cb.equal(r.get("requestedBy"), scope.userId()) : null);
        var found = repo.findAll(spec, page.toPageable(Set.of("id", "requestedAt"),
                Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = new ArrayList<>();
        found.forEach(a -> {
            ids.add(a.requestedBy);
            if (a.decidedBy != null) {
                ids.add(a.decidedBy);
            }
        });
        Map<Long, String> names = users.displayNames(ids);
        return PageResponse.of(found.map(a -> a.toResponse(names)));
    }

    private Optional<ApprovalRequest> findPending(CreateApprovalRequest req) {
        return repo.findByTypeAndSubjectTypeAndSubjectIdAndSubjectVersionAndStatus(
                req.type(), req.subjectType(), req.subjectId(), req.subjectVersion(), ApprovalStatus.PENDING);
    }

    private ApprovalResponse toResponse(ApprovalRequest a) {
        List<Long> ids = a.decidedBy == null ? List.of(a.requestedBy) : List.of(a.requestedBy, a.decidedBy);
        return a.toResponse(users.displayNames(ids));
    }

    private static void requirePending(ApprovalRequest a) {
        if (a.status != ApprovalStatus.PENDING) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This request was already " + a.status.name().toLowerCase());
        }
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Approval not found");
    }
}
