package ph.thecoffeejunkie.crm.deals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.customers.CustomerSalesProfile;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.sales.OrderBrief;
import ph.thecoffeejunkie.crm.sales.OrderStatus;
import ph.thecoffeejunkie.crm.sales.Orders;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.time.Instant;
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
    private final DealOrderRepository links;
    private final Orders orders;
    private final Customers customers;
    private final Users users;
    private final AuditLog audit;

    DealService(DealRepository repo, DealOrderRepository links, Orders orders, Customers customers, Users users,
                AuditLog audit) {
        this.repo = repo;
        this.links = links;
        this.orders = orders;
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

    @Transactional
    DealResponse update(Scope s, long id, DealRequest req) {
        Deal d = findVisible(s, id);
        requireOpen(d);
        requireVersion(d, req.version());
        AuditChanges changes = AuditChanges.diff().field("title", d.title, req.title())
                .field("value", d.value, req.value()).field("expectedClose", d.expectedClose, req.expectedClose())
                .field("notes", d.notes, req.notes());
        d.title = req.title();
        d.value = req.value();
        d.expectedClose = req.expectedClose();
        d.notes = req.notes();
        if (!changes.isEmpty()) {
            touch(d);
            audit.record(AuditAction.DEAL_UPDATED, ENTITY, id, "Updated deal \"" + d.title + "\"", changes);
        }
        return toResponse(d);
    }

    /** Open stages move freely; Lost needs a reason and only reopens; Won is final (see {@link #markWon}). */
    @Transactional
    DealResponse setStage(Scope s, long id, DealStageRequest req) {
        Deal d = findVisible(s, id);
        if (d.stage == DealStage.WON || (d.stage == DealStage.LOST && !DealStage.OPEN.contains(req.stage()))) {
            requireOpen(d);
        }
        requireVersion(d, req.version());
        if (req.stage() == DealStage.WON) {
            long orderId = confirmedOrder(d).orElseThrow(() -> new DomainException(ErrorCode.DEAL_WON_NEEDS_ORDER,
                    "Link a confirmed order before marking this deal won."));
            markWon(d, orderId);
            return toResponse(d);
        }
        if (req.stage() == DealStage.LOST && req.lostReason() == null) {
            throw new DomainException(ErrorCode.DEAL_LOST_REASON, "Say why the deal was lost.");
        }
        String reason = req.stage() == DealStage.LOST ? req.lostReason() : null;
        if (d.stage != req.stage() || !Objects.equals(d.lostReason, reason)) {
            AuditChanges changes = AuditChanges.diff().field("stage", d.stage, req.stage())
                    .field("lostReason", d.lostReason, reason);
            d.stage = req.stage();
            d.lostReason = reason;
            touch(d);
            audit.record(AuditAction.DEAL_STAGE_CHANGED, ENTITY, id, "Deal \"" + d.title + "\" is now " + d.stage,
                    changes);
        }
        return toResponse(d);
    }

    /** The earliest confirmed linked order, if any (confirmed, released or fulfilled). */
    java.util.Optional<Long> confirmedOrder(Deal d) {
        return ordersOf(d).stream().filter(o -> o.confirmedAt() != null && (o.status() == OrderStatus.CONFIRMED
                        || o.status() == OrderStatus.RELEASED || o.status() == OrderStatus.FULFILLED))
                .min(java.util.Comparator.comparing(OrderBrief::confirmedAt)).map(OrderBrief::id);
    }

    @Transactional
    void moveOpen(long customerId, long toRepId) {
        for (Deal d : repo.findByCustomerIdAndStageIn(customerId, DealStage.OPEN)) {
            if (Objects.equals(d.repId, toRepId)) {
                continue;
            }
            Long from = d.repId;
            d.repId = toRepId;
            touch(d);
            audit.record(AuditAction.DEAL_REASSIGNED, ENTITY, d.id, "Moved deal \"" + d.title + "\" with its customer",
                    AuditChanges.diff().field("repId", from, toRepId));
        }
    }

    /** A linked order was confirmed: an open deal is won by it; a Won or Lost deal is left alone. */
    @Transactional
    void orderConfirmed(long orderId) {
        links.findByOrderId(orderId).flatMap(l -> repo.findById(l.dealId))
                .filter(d -> DealStage.OPEN.contains(d.stage))
                .ifPresent(d -> markWon(d, orderId));
    }

    void markWon(Deal d, long orderId) {
        d.stage = DealStage.WON;
        d.lostReason = null;
        d.wonAt = Instant.now();
        d.wonOrderId = orderId;
        touch(d);
        audit.record(AuditAction.DEAL_WON, ENTITY, d.id, "Won deal \"" + d.title + "\"",
                AuditChanges.diff().field("wonOrderId", null, orderId));
    }

    void touch(Deal d) {
        d.updatedAt = Instant.now();
        repo.saveAndFlush(d);
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

    /** Linked orders in link order. */
    List<OrderBrief> ordersOf(Deal d) {
        List<Long> ids = links.findByDealIdOrderByLinkedAtAscOrderIdAsc(d.id).stream().map(l -> l.orderId).toList();
        Map<Long, OrderBrief> by = new java.util.HashMap<>();
        orders.briefs(ids).forEach(b -> by.put(b.id(), b));
        return ids.stream().map(by::get).filter(Objects::nonNull).toList();
    }

    /**
     * Links a DRAFT/QUOTED order of the deal's customer that is on no other deal. Anything else, including an
     * order that does not exist, is the same 422 so order ids and numbers cannot be probed through a deal.
     */
    @Transactional
    DealResponse link(Scope s, long id, long orderId) {
        Deal d = findVisible(s, id);
        requireOpen(d);
        java.util.Optional<DealOrder> existing = links.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().dealId.equals(d.id)) {
            return toResponse(d);
        }
        OrderBrief o = orders.brief(orderId).orElse(null);
        if (existing.isPresent() || o == null || o.customerId() != d.customerId
                || (o.status() != OrderStatus.DRAFT && o.status() != OrderStatus.QUOTED)) {
            throw mismatch();
        }
        DealOrder l = new DealOrder();
        l.dealId = d.id;
        l.orderId = orderId;
        l.linkedBy = s.userId();
        try {
            links.saveAndFlush(l);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw mismatch(); // linked to another deal at the same moment
        }
        audit.record(AuditAction.DEAL_ORDER_LINKED, ENTITY, d.id, "Linked order " + o.number() + " to deal \""
                + d.title + "\"", null);
        return toResponse(d);
    }

    @Transactional
    DealResponse unlink(Scope s, long id, long orderId) {
        Deal d = findVisible(s, id);
        requireOpen(d);
        links.findById(new DealOrder.Key(d.id, orderId)).ifPresent(l -> {
            links.delete(l);
            links.flush();
            String number = orders.brief(orderId).map(OrderBrief::number).orElse("#" + orderId);
            audit.record(AuditAction.DEAL_ORDER_UNLINKED, ENTITY, d.id, "Unlinked order " + number + " from deal \""
                    + d.title + "\"", null);
        });
        return toResponse(d);
    }

    /** The deal an order is on, if the caller can see that deal. */
    @Transactional(readOnly = true)
    java.util.Optional<DealRef> byOrder(Scope s, long orderId) {
        return links.findByOrderId(orderId)
                .flatMap(l -> repo.findOne(DealScopes.visibleTo(s).and(DealScopes.eq("id", l.dealId))))
                .map(d -> new DealRef(d.id, d.title));
    }

    private static DomainException mismatch() {
        return new DomainException(ErrorCode.DEAL_ORDER_MISMATCH,
                "Only a draft or quoted order of this customer that is on no other deal can be linked.");
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
