package ph.thecoffeejunkie.crm.followups;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Follow-ups (spec 2026-11-05): a rep sees and works their own; an admin sees and assigns everyone's. */
@Service
class FollowUpService {

    static final String ENTITY = "FOLLOW_UP";

    private final FollowUpRepository repo;
    private final FollowUpQueries queries;
    private final Customers customers;
    private final Users users;
    private final AuditLog audit;
    private final Clock clock;

    FollowUpService(FollowUpRepository repo, FollowUpQueries queries, Customers customers, Users users, AuditLog audit,
                    Clock clock) {
        this.repo = repo;
        this.queries = queries;
        this.customers = customers;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    FollowUpResponse create(Scope s, FollowUpRequest req) {
        CustomerSalesProfile c = customers.salesProfileVisible(s, req.customerId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        checkOrder(req.orderId(), c.id());
        FollowUp f = new FollowUp();
        f.customerId = c.id();
        f.orderId = req.orderId();
        f.assigneeId = s.role() == Role.SALES ? s.userId() : adminAssignee(req.assigneeId(), c);
        f.title = req.title().trim();
        f.note = blankToNull(req.note());
        f.dueDate = req.dueDate();
        f.createdBy = s.userId();
        repo.saveAndFlush(f);
        audit.record(AuditAction.FOLLOW_UP_CREATED, ENTITY, f.id, "Created follow-up \"" + f.title + "\"", null);
        return toResponses(List.of(f)).getFirst();
    }

    @Transactional(readOnly = true)
    PageResponse<FollowUpResponse> list(Scope s, FollowUpDay.Bucket bucket, Long customerId, Long orderId,
                                        Long assigneeId, int page, int size) {
        LocalDate today = FollowUpDay.today(clock);
        Specification<FollowUp> spec = visibleTo(s, assigneeId).and(inBucket(bucket, today))
                .and(eq("customerId", customerId)).and(eq("orderId", orderId));
        Sort sort = bucket == FollowUpDay.Bucket.DONE ? Sort.by(Sort.Order.desc("doneAt"), Sort.Order.desc("id"))
                : Sort.by("dueDate", "id");
        Page<FollowUp> rows = repo.findAll(spec, PageRequest.of(page - 1, size, sort));
        List<FollowUpResponse> content = toResponses(rows.getContent());
        return new PageResponse<>(rows.getNumber() + 1, rows.getSize(), rows.getTotalPages(), rows.getTotalElements(),
                content);
    }

    @Transactional(readOnly = true)
    FollowUpCounts counts(Scope s, Long customerId, Long orderId) {
        return queries.counts(s.isAdmin() ? null : s.userId(), customerId, orderId, FollowUpDay.today(clock));
    }

    private long adminAssignee(Long requested, CustomerSalesProfile c) {
        Long id = requested != null ? requested : c.assignedRepId();
        if (id == null) {
            throw new DomainException(ErrorCode.FOLLOW_UP_NO_ASSIGNEE, "This customer has no rep. Choose who follows up.");
        }
        requireRep(id);
        return id;
    }

    void requireRep(long id) {
        if (!users.isActiveWithRole(id, Role.SALES)) {
            throw new DomainException(ErrorCode.FOLLOW_UP_NOT_A_REP, "Follow-ups go to an active sales rep.");
        }
    }

    void checkOrder(Long orderId, long customerId) {
        if (orderId != null && !queries.orderCustomer(orderId).map(c -> c == customerId).orElse(false)) {
            throw new DomainException(ErrorCode.FOLLOW_UP_ORDER_MISMATCH, "That order belongs to another customer.");
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** SALES only ever sees their own; ADMIN may narrow by assignee. */
    static Specification<FollowUp> visibleTo(Scope s, Long assigneeId) {
        return eq("assigneeId", s.isAdmin() ? assigneeId : Long.valueOf(s.userId()));
    }

    private static Specification<FollowUp> inBucket(FollowUpDay.Bucket b, LocalDate today) {
        return (r, q, cb) -> switch (b) {
            case OVERDUE -> cb.and(cb.isNull(r.get("doneAt")), cb.lessThan(r.get("dueDate"), today));
            case TODAY -> cb.and(cb.isNull(r.get("doneAt")), cb.equal(r.get("dueDate"), today));
            case UPCOMING -> cb.and(cb.isNull(r.get("doneAt")), cb.greaterThan(r.get("dueDate"), today));
            case DONE -> cb.isNotNull(r.get("doneAt"));
        };
    }

    private static Specification<FollowUp> eq(String field, Object value) {
        return (r, q, cb) -> value == null ? null : cb.equal(r.get(field), value);
    }

    List<FollowUpResponse> toResponses(List<FollowUp> rows) {
        Map<Long, String> names = customers.displayNames(rows.stream().map(f -> f.customerId).distinct().toList());
        Map<Long, String> people = users.displayNames(rows.stream().map(f -> f.assigneeId).distinct().toList());
        Map<Long, String> orders = queries.orderNumbers(rows.stream().map(f -> f.orderId).filter(Objects::nonNull)
                .distinct().toList());
        return rows.stream().map(f -> new FollowUpResponse(f.id, f.customerId, names.get(f.customerId), f.orderId,
                f.orderId == null ? null : orders.get(f.orderId), f.assigneeId, people.get(f.assigneeId), f.title, f.note,
                f.dueDate, f.doneAt, f.version)).toList();
    }
}
