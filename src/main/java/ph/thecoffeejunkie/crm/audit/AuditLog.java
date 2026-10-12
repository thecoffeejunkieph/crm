package ph.thecoffeejunkie.crm.audit;

import org.slf4j.MDC;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.time.Instant;
import java.util.Set;

/** Writes join the caller's transaction (MANDATORY) so an audit row never outlives a rolled-back change. */
@Service
public class AuditLog {

    private static final Set<String> SORTABLE = Set.of("at", "id");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "at");

    private final AuditEventRepository repo;
    private final CurrentActor actor;

    AuditLog(AuditEventRepository repo, CurrentActor actor) {
        this.repo = repo;
        this.actor = actor;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, Long entityId, String summary, AuditChanges changes) {
        AuditEvent e = base(action, entityType, entityId, summary);
        if (changes != null && !changes.isEmpty()) {
            e.changes = changes.toMap();
        }
        actor.staff().ifPresentOrElse(s -> {
            e.actorType = "USER";
            e.actorId = s.userId();
            e.actorName = s.displayName();
        }, () -> actor.contact().ifPresentOrElse(c -> {
            e.actorType = "CONTACT";
            e.actorId = c.contactId();
            e.actorName = c.displayName();
        }, () -> e.actorType = "SYSTEM"));
        repo.save(e);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFor(String actorType, Long actorId, String actorName, AuditAction action, String entityType,
                          Long entityId, String summary) {
        AuditEvent e = base(action, entityType, entityId, summary);
        e.actorType = actorType;
        e.actorId = actorId;
        e.actorName = actorName;
        repo.save(e);
    }

    /** Callers enforce scope on the entity first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> history(String entityType, long entityId, PageQuery page) {
        return query(null, null, entityType, null, null, null, entityId, page);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(String actorType, Long actorId, String entityType,
                                                   AuditAction action, Instant from, Instant to, PageQuery page) {
        return query(actorType, actorId, entityType, action, from, to, null, page);
    }

    private PageResponse<AuditEventResponse> query(String actorType, Long actorId, String entityType,
                                                   AuditAction action, Instant from, Instant to, Long entityId,
                                                   PageQuery page) {
        Specification<AuditEvent> spec = Specification.allOf(
                eq("actorType", actorType), eq("actorId", actorId), eq("entityType", entityType),
                eq("action", action == null ? null : action.name()), eq("entityId", entityId),
                (r, q, cb) -> from == null ? null : cb.greaterThanOrEqualTo(r.<Instant>get("at"), from),
                (r, q, cb) -> to == null ? null : cb.lessThan(r.<Instant>get("at"), to));
        return PageResponse.of(repo.findAll(spec, page.toPageable(SORTABLE, DEFAULT_SORT))
                .map(AuditEvent::toResponse));
    }

    private static Specification<AuditEvent> eq(String field, Object value) {
        return (r, q, cb) -> value == null ? null : cb.equal(r.get(field), value);
    }

    private static AuditEvent base(AuditAction action, String entityType, Long entityId, String summary) {
        AuditEvent e = new AuditEvent();
        e.action = action.name();
        e.entityType = entityType;
        e.entityId = entityId;
        e.summary = summary;
        e.requestId = MDC.get("requestId");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            e.ip = a.getRequest().getRemoteAddr();
        }
        return e;
    }
}
