package ph.thecoffeejunkie.crm.deals;

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
import ph.thecoffeejunkie.crm.sales.OrderBrief;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deals (spec 2026-11-06): a rep works deals on their own customers; an admin sees all. */
@Service
class DealService {

    static final String ENTITY = "DEAL";
    private static final Sort SORT = Sort.by(Sort.Order.asc("expectedClose").nullsLast(), Sort.Order.asc("id"));

    private final DealRepository repo;
    private final Customers customers;
    private final Users users;
    private final AuditLog audit;

    DealService(DealRepository repo, Customers customers, Users users, AuditLog audit) {
        this.repo = repo;
        this.customers = customers;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    DealResponse create(Scope s, DealRequest req) {
        if (req.customerId() == null) {
            throw invalid("customerId", "Choose a customer.");
        }
        CustomerSalesProfile c = customers.salesProfileVisible(s, req.customerId()).filter(p -> !p.archived())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Customer not found"));
        Deal d = new Deal();
        d.customerId = c.id();
        d.repId = c.assignedRepId();
        d.title = req.title();
        d.value = req.value();
        d.expectedClose = req.expectedClose();
        d.notes = req.notes();
        d.createdBy = s.userId();
        repo.saveAndFlush(d);
        audit.record(AuditAction.DEAL_CREATED, ENTITY, d.id, "Created deal \"" + d.title + "\"", null);
        return toResponse(d);
    }

    @Transactional(readOnly = true)
    DealResponse get(Scope s, long id) {
        return toResponse(findVisible(s, id));
    }

    @Transactional(readOnly = true)
    PageResponse<DealResponse> list(Scope s, DealStage stage, String q, Long customerId, Long repId, int page,
                                    int size) {
        Specification<Deal> spec = DealScopes.visibleTo(s)
                .and(DealScopes.matches(q, customers.idsNamed(q)))
                .and(DealScopes.eq("customerId", customerId))
                .and(DealScopes.eq("repId", s.isAdmin() ? repId : null))
                .and(stage != null ? DealScopes.eq("stage", stage) : DealScopes.stageIn(DealStage.OPEN));
        Page<Deal> rows = repo.findAll(spec, PageRequest.of(page - 1, size, SORT));
        return new PageResponse<>(rows.getNumber() + 1, rows.getSize(), rows.getTotalPages(), rows.getTotalElements(),
                toResponses(rows.getContent()));
    }

    @Transactional(readOnly = true)
    DealSummary summary(Scope s) {
        Map<DealStage, Object[]> by = new EnumMap<>(DealStage.class);
        for (Object[] row : repo.totalsByStage(s.isAdmin() ? null : s.userId())) {
            by.put((DealStage) row[0], row);
        }
        List<DealSummary.StageTotal> stages = new ArrayList<>();
        long openCount = 0;
        BigDecimal openValue = BigDecimal.ZERO;
        for (DealStage st : DealStage.values()) {
            Object[] row = by.get(st);
            long count = row == null ? 0 : (Long) row[1];
            BigDecimal value = row == null ? BigDecimal.ZERO : (BigDecimal) row[2];
            stages.add(new DealSummary.StageTotal(st, count, value));
            if (DealStage.OPEN.contains(st)) {
                openCount += count;
                openValue = openValue.add(value);
            }
        }
        return new DealSummary(stages, openCount, openValue);
    }

    Deal findVisible(Scope s, long id) {
        return repo.findOne(DealScopes.visibleTo(s).and(DealScopes.eq("id", id)))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Deal not found"));
    }

    static void requireOpen(Deal d) {
        if (!DealStage.OPEN.contains(d.stage)) {
            throw new DomainException(ErrorCode.DEAL_CLOSED, d.stage == DealStage.WON
                    ? "This deal is won and can't be changed." : "This deal is lost. Reopen it first.");
        }
    }

    static void requireVersion(Deal d, Long sent) {
        if (sent == null) {
            throw invalid("version", "must not be null");
        }
        if (sent != d.version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "Someone else changed this deal. Reload and retry.");
        }
    }

    static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }

    /** The deal's linked orders; filled from Task 3 on. */
    List<OrderBrief> ordersOf(Deal d) {
        return List.of();
    }

    DealResponse toResponse(Deal d) {
        return toResponses(List.of(d)).getFirst();
    }

    List<DealResponse> toResponses(List<Deal> rows) {
        Map<Long, String> names = customers.displayNames(rows.stream().map(d -> d.customerId).distinct().toList());
        Map<Long, String> reps = users.displayNames(rows.stream().map(d -> d.repId).filter(Objects::nonNull)
                .distinct().toList());
        return rows.stream().map(d -> new DealResponse(d.id, d.customerId, names.get(d.customerId), d.repId,
                d.repId == null ? null : reps.get(d.repId), d.title, d.stage, d.value, d.expectedClose, d.lostReason,
                d.notes, d.wonAt, d.wonOrderId, ordersOf(d), d.createdAt, d.updatedAt, d.version)).toList();
    }
}
