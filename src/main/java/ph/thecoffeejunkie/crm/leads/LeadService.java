package ph.thecoffeejunkie.crm.leads;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.customers.LeadSources;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Leads (spec 2026-11-05): a rep works their own; an admin sees and assigns everyone's. */
@Service
class LeadService {

    static final String ENTITY = "LEAD";

    private final LeadRepository repo;
    private final LeadSources sources;
    private final Customers customers;
    private final Users users;
    private final AuditLog audit;

    LeadService(LeadRepository repo, LeadSources sources, Customers customers, Users users, AuditLog audit) {
        this.repo = repo;
        this.sources = sources;
        this.customers = customers;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    LeadResponse create(Scope s, LeadRequest req) {
        requireContact(req);
        sources.requireActive(req.sourceId());
        Lead l = new Lead();
        if (s.role() == Role.SALES) {
            l.assignedRepId = s.userId();
        } else if (req.assignedRepId() != null) {
            requireRep(req.assignedRepId());
            l.assignedRepId = req.assignedRepId();
        }
        apply(l, req);
        l.createdBy = s.userId();
        repo.saveAndFlush(l);
        audit.record(AuditAction.LEAD_CREATED, ENTITY, l.id, "Created lead " + l.contactName, null);
        return toResponse(l);
    }

    @Transactional
    LeadResponse update(Scope s, long id, LeadRequest req) {
        Lead l = findVisible(s, id);
        requireNotConverted(l);
        requireVersion(l, req.version());
        requireContact(req);
        if (!Objects.equals(l.sourceId, req.sourceId())) {
            sources.requireActive(req.sourceId()); // an archived source may stay, but not be newly chosen
        }
        AuditChanges changes = AuditChanges.diff().field("contactName", l.contactName, req.contactName())
                .field("company", l.company, req.company()).field("phone", l.phone, req.phone())
                .field("email", l.email, req.email()).field("socialMedia", l.socialMedia, req.socialMedia())
                .field("address", l.address, req.address()).field("interest", l.interest, req.interest())
                .field("notes", l.notes, req.notes()).field("sourceId", l.sourceId, req.sourceId());
        apply(l, req);
        if (!changes.isEmpty()) {
            touch(l);
            audit.record(AuditAction.LEAD_UPDATED, ENTITY, id, "Updated lead " + l.contactName, changes);
        }
        return toResponse(l);
    }

    @Transactional
    LeadResponse setStatus(Scope s, long id, LeadStatusRequest req) {
        Lead l = findVisible(s, id);
        requireNotConverted(l);
        requireVersion(l, req.version());
        if (req.status() == LeadStatus.CONVERTED) {
            throw invalid("status", "Convert the lead to set it to Converted.");
        }
        if (req.status() == LeadStatus.LOST && req.lostReason() == null) {
            throw new DomainException(ErrorCode.LEAD_LOST_REASON, "Say why the lead was lost.");
        }
        String reason = req.status() == LeadStatus.LOST ? req.lostReason() : null;
        if (l.status != req.status() || !Objects.equals(l.lostReason, reason)) {
            AuditChanges changes = AuditChanges.diff().field("status", l.status, req.status())
                    .field("lostReason", l.lostReason, reason);
            l.status = req.status();
            l.lostReason = reason;
            touch(l);
            audit.record(AuditAction.LEAD_STATUS_CHANGED, ENTITY, id, "Lead " + l.contactName + " is now "
                    + l.status, changes);
        }
        return toResponse(l);
    }

    @Transactional
    LeadResponse assign(Scope s, long id, LeadAssignRequest req) {
        Lead l = findVisible(s, id);
        requireNotConverted(l);
        requireVersion(l, req.version());
        if (req.repId() != null) {
            requireRep(req.repId());
        }
        if (!Objects.equals(l.assignedRepId, req.repId())) {
            AuditChanges changes = AuditChanges.diff().field("assignedRepId", l.assignedRepId, req.repId());
            l.assignedRepId = req.repId();
            touch(l);
            audit.record(AuditAction.LEAD_ASSIGNED, ENTITY, id, "Reassigned lead " + l.contactName, changes);
        }
        return toResponse(l);
    }

    static void requireNotConverted(Lead l) {
        if (l.status == LeadStatus.CONVERTED) {
            throw new DomainException(ErrorCode.LEAD_CONVERTED, "This lead is already a customer.");
        }
    }

    static void requireVersion(Lead l, Long sent) {
        if (sent == null) {
            throw invalid("version", "must not be null");
        }
        if (sent != l.version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "Someone else changed this lead. Reload and retry.");
        }
    }

    private void touch(Lead l) {
        l.updatedAt = Instant.now();
        repo.saveAndFlush(l);
    }

    @Transactional(readOnly = true)
    LeadResponse get(Scope s, long id) {
        return toResponse(findVisible(s, id));
    }

    @Transactional(readOnly = true)
    PageResponse<LeadResponse> list(Scope s, LeadStatus status, String q, Long sourceId, Long repId, int page, int size) {
        Specification<Lead> byStatus = status != null ? LeadScopes.eq("status", status)
                : (r, cq, cb) -> r.get("status").in(LeadStatus.OPEN);
        Specification<Lead> spec = LeadScopes.visibleTo(s).and(LeadScopes.matches(q))
                .and(LeadScopes.eq("sourceId", sourceId))
                .and(LeadScopes.eq("assignedRepId", s.isAdmin() ? repId : null))
                .and(byStatus);
        Page<Lead> rows = repo.findAll(spec, PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(rows.getNumber() + 1, rows.getSize(), rows.getTotalPages(), rows.getTotalElements(),
                toResponses(rows.getContent()));
    }

    @Transactional(readOnly = true)
    LeadCounts counts(Scope s) {
        Map<LeadStatus, Long> by = new EnumMap<>(LeadStatus.class);
        for (Object[] row : repo.countByStatus(s.isAdmin() ? null : s.userId())) {
            by.put((LeadStatus) row[0], (Long) row[1]);
        }
        long n = by.getOrDefault(LeadStatus.NEW, 0L);
        long c = by.getOrDefault(LeadStatus.CONTACTED, 0L);
        long qd = by.getOrDefault(LeadStatus.QUALIFIED, 0L);
        return new LeadCounts(n + c + qd, n, c, qd, by.getOrDefault(LeadStatus.LOST, 0L),
                by.getOrDefault(LeadStatus.CONVERTED, 0L));
    }

    Lead findVisible(Scope s, long id) {
        return repo.findOne(LeadScopes.visibleTo(s).and(LeadScopes.eq("id", id)))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Lead not found"));
    }

    void requireRep(long id) {
        if (!users.isActiveWithRole(id, Role.SALES)) {
            throw invalid("assignedRepId", "Choose an active sales user.");
        }
    }

    static void requireContact(LeadRequest req) {
        if (req.phone() == null && req.email() == null && req.socialMedia() == null) {
            throw new DomainException(ErrorCode.LEAD_CONTACT_REQUIRED, "Add a phone, email or social link.");
        }
    }

    static void apply(Lead l, LeadRequest r) {
        l.contactName = r.contactName();
        l.company = r.company();
        l.phone = r.phone();
        l.email = r.email();
        l.socialMedia = r.socialMedia();
        l.address = r.address();
        l.interest = r.interest();
        l.notes = r.notes();
        l.sourceId = r.sourceId();
    }

    static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    LeadResponse toResponse(Lead l) {
        return toResponses(List.of(l)).getFirst();
    }

    List<LeadResponse> toResponses(List<Lead> rows) {
        Map<Long, String> sourceNames = sources.names(rows.stream().map(l -> l.sourceId).toList());
        Map<Long, String> reps = users.displayNames(rows.stream().map(l -> l.assignedRepId).filter(Objects::nonNull)
                .distinct().toList());
        Map<Long, String> custs = customers.displayNames(rows.stream().map(l -> l.customerId).filter(Objects::nonNull)
                .distinct().toList());
        return rows.stream().map(l -> new LeadResponse(l.id, l.contactName, l.company, l.phone, l.email, l.socialMedia,
                l.address, l.interest, l.notes, l.sourceId, nameOf(sourceNames, l.sourceId), l.assignedRepId,
                nameOf(reps, l.assignedRepId), l.status, l.lostReason, l.customerId, nameOf(custs, l.customerId),
                l.convertedAt, l.createdAt, l.updatedAt, l.version)).toList();
    }

    /** Immutable maps throw on a null key. */
    private static String nameOf(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }
}
