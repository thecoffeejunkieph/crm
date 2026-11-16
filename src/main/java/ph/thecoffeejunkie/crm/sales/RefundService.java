package ph.thecoffeejunkie.crm.sales;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.approvals.ApprovalResponse;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.CreateApprovalRequest;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PdfFile;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Refund requests against a sales order. Every write takes the order row lock first, so the refundable and
 * returnable checks can't race: two requests that together exceed either are serialised and the second is refused.
 */
@Service
class RefundService {

    static final String ENTITY = "REFUND";

    private record Key(long orderLineId, long warehouseId) {
    }

    private final RefundRepository repo;
    private final OrderAccess access;
    private final Approvals approvals;
    private final AuditLog audit;
    private final Users users;
    private final StorageService storage;
    private final JdbcTemplate jdbc;
    private final Inventory inventory;
    private final DocumentCounters counters;
    private final Notifications notifications;
    private final ApplicationEventPublisher events;
    private final CreditMemoDocument creditMemo;

    RefundService(RefundRepository repo, OrderAccess access, Approvals approvals, AuditLog audit, Users users,
                  StorageService storage, JdbcTemplate jdbc, Inventory inventory, DocumentCounters counters,
                  Notifications notifications, ApplicationEventPublisher events, CreditMemoDocument creditMemo) {
        this.creditMemo = creditMemo;
        this.inventory = inventory;
        this.counters = counters;
        this.notifications = notifications;
        this.events = events;
        this.repo = repo;
        this.access = access;
        this.approvals = approvals;
        this.audit = audit;
        this.users = users;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    /** An admin's request is APPROVED at once (and audited as self-approved); anyone else's waits for an admin. */
    @Transactional
    RefundResponse request(Scope s, long orderId, RefundRequest req) {
        SalesOrder o = access.findVisibleForUpdate(orderId, s);
        Refund r = new Refund();
        r.orderId = o.id;
        r.kind = req.kind();
        r.reason = req.reason().trim();
        r.requestedBy = s.userId();
        if (req.kind() == RefundKind.GOODS_RETURN) {
            addLines(r, o, req);
        } else {
            r.amount = amountOf(req);
        }
        requireWithinRefundable(o, r.amount, repo.openAmount(o.id));
        r.status = s.isAdmin() ? RefundStatus.APPROVED : RefundStatus.REQUESTED;
        repo.saveAndFlush(r);
        Map<String, Object> value = Map.of("amount", r.amount, "orderNumber", o.number);
        if (s.isAdmin()) {
            approvals.ensureApproved(ApprovalType.REFUND, ENTITY, r.id, 0, value);
        } else {
            // Approvals joins this transaction, so the refund and its request commit or roll back together.
            ApprovalResponse ar = approvals.requestOnBehalf(
                    new CreateApprovalRequest(ApprovalType.REFUND, ENTITY, r.id, 0, value, r.reason), s.userId());
            r.approvalRequestId = ar.id();
        }
        audit.record(AuditAction.REFUND_REQUESTED, OrderService.ENTITY, o.id, "Requested a " + r.amount.toPlainString()
                + " refund (" + r.kind.name().toLowerCase().replace('_', ' ') + "): " + r.reason, null);
        return toResponse(r, o);
    }

    /** Called when the refund's approval is decided. Anything no longer REQUESTED (e.g. cancelled meanwhile) stays. */
    @Transactional
    void decided(long refundId, boolean approved) {
        Refund r = repo.findByIdForUpdate(refundId).orElse(null);
        if (r == null || r.status != RefundStatus.REQUESTED) {
            return;
        }
        r.status = approved ? RefundStatus.APPROVED : RefundStatus.REJECTED;
        r.updatedAt = Instant.now();
    }

    /** REQUESTED or APPROVED only: once paid the money is gone, once rejected there is nothing to cancel. */
    @Transactional
    RefundResponse cancel(Scope s, long refundId, long version) {
        Refund found = repo.findById(refundId).orElseThrow(RefundService::notFound);
        SalesOrder o = access.findVisibleForUpdate(found.orderId, s);
        Refund r = repo.findByIdForUpdate(refundId).orElseThrow(RefundService::notFound);
        if (r.version != version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This refund was changed by someone else. Reload.");
        }
        if (r.status != RefundStatus.REQUESTED && r.status != RefundStatus.APPROVED) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Can't cancel a " + r.status.name().toLowerCase() + " refund");
        }
        if (r.status == RefundStatus.REQUESTED && r.approvalRequestId != null) {
            approvals.cancelForSubject(r.approvalRequestId);
        }
        r.status = RefundStatus.CANCELLED;
        r.updatedAt = Instant.now();
        repo.saveAndFlush(r);
        audit.record(AuditAction.REFUND_CANCELLED, OrderService.ENTITY, o.id,
                "Cancelled the " + r.amount.toPlainString() + " refund", null);
        return toResponse(r, o);
    }

    /**
     * Pays an APPROVED refund out: issues the credit memo number, counts the money as refunded and brings returned
     * goods back into the chosen warehouses, all in one transaction under the order lock. The guards run again
     * against what has actually been paid, since other refunds may have been paid since this one was approved.
     */
    @Transactional
    RefundResponse payout(Scope s, long refundId, RefundPayoutRequest req, MultipartFile proof) {
        Refund found = repo.findById(refundId).orElseThrow(RefundService::notFound);
        SalesOrder o = access.findVisibleForUpdate(found.orderId, s);
        Refund r = repo.findByIdForUpdate(refundId).orElseThrow(RefundService::notFound);
        if (r.version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This refund was changed by someone else. Reload.");
        }
        if (r.status != RefundStatus.APPROVED) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Can't pay out a " + r.status.name().toLowerCase() + " refund");
        }
        // Other open refunds are checked at their own payout, so only money already paid out counts here.
        requireWithinRefundable(o, r.amount, BigDecimal.ZERO);
        // This refund's own lines are inside "returned", so the room left must not be negative.
        Map<Key, BigDecimal> room = new LinkedHashMap<>();
        returnable(o.id).forEach(t -> room.put(new Key(t.orderLineId(), t.warehouseId()),
                t.shipped().subtract(t.returned())));
        for (RefundLine l : r.lines) {
            BigDecimal left = room.getOrDefault(new Key(l.orderLineId, l.warehouseId), BigDecimal.ZERO);
            if (left.signum() < 0) {
                throw new DomainException(ErrorCode.OVERPAYMENT, "Can't return more than was shipped from that warehouse.",
                        Map.of("returnable", left.add(l.qty).max(BigDecimal.ZERO)));
            }
        }
        r.method = req.method();
        r.reference = req.reference();
        r.paidOn = req.paidOn();
        r.proofKey = proof == null || proof.isEmpty() ? null
                : storage.store("refunds/proof", proof, StorageService.PROOF_TYPES);
        r.paidBy = s.userId();
        r.paidAt = Instant.now();
        r.creditMemoNo = "CM-%05d".formatted(counters.next("CREDIT_MEMO"));
        r.status = RefundStatus.PAID;
        r.updatedAt = r.paidAt;
        o.refundedAmount = o.refundedAmount.add(r.amount);
        o.updatedAt = r.paidAt;
        repo.saveAndFlush(r);
        for (RefundLine l : r.lines) {
            long productId = o.lines.stream().filter(ol -> ol.id.equals(l.orderLineId)).findFirst().orElseThrow().productId;
            inventory.receiveReturn(l.warehouseId, productId, l.qty, l.disposition == RefundDisposition.DAMAGED,
                    r.creditMemoNo, r.id);
        }
        audit.record(AuditAction.REFUND_PAID, OrderService.ENTITY, o.id, "Paid the " + r.amount.toPlainString()
                + " refund (" + r.creditMemoNo + ")", null);
        Set<Long> recipients = new HashSet<>(users.activeIdsWithRole(Role.ADMIN));
        recipients.add(OrderStockMapper.rep(o));
        notifications.toUsers(recipients, NotificationType.REFUND_PAID, OrderService.ENTITY, o.id, "Refund paid",
                "Refund ₱%s paid on %s (%s)".formatted(r.amount.toPlainString(), o.number, r.creditMemoNo));
        events.publishEvent(new RefundPaid(o.id, r.id, o.repId, r.amount, r.paidAt));
        return toResponse(r, o);
    }

    @Transactional(readOnly = true)
    PdfFile creditMemoPdf(Scope s, long refundId) {
        Refund r = repo.findById(refundId).orElseThrow(RefundService::notFound);
        return creditMemo.render(r, access.findVisible(r.orderId, s));
    }

    @Transactional(readOnly = true)
    List<RefundResponse> list(Scope s, long orderId) {
        SalesOrder o = access.findVisible(orderId, s);
        return repo.findByOrderIdOrderByIdDesc(orderId).stream().map(r -> toResponse(r, o)).toList();
    }

    @Transactional(readOnly = true)
    List<Returnable> returnable(Scope s, long orderId) {
        access.findVisible(orderId, s);
        return returnable(orderId);
    }

    /** Dispatched picks per order line and warehouse, and what non-void refunds already claim of them. */
    @Transactional(readOnly = true)
    List<Returnable> returnable(long orderId) {
        Map<Key, BigDecimal[]> byKey = new LinkedHashMap<>();
        jdbc.query("select dol.order_line_id, d.warehouse_id, sum(dol.picked_qty) from delivery_order_line dol "
                + "join delivery_order d on d.id = dol.delivery_order_id where d.sales_order_id = ? "
                + "and d.dispatched_at is not null group by dol.order_line_id, d.warehouse_id "
                + "order by dol.order_line_id, d.warehouse_id", rs -> {
            byKey.put(new Key(rs.getLong(1), rs.getLong(2)), new BigDecimal[]{rs.getBigDecimal(3), BigDecimal.ZERO});
        }, orderId);
        jdbc.query("select rl.order_line_id, rl.warehouse_id, sum(rl.qty) from refund_line rl "
                + "join refund r on r.id = rl.refund_id where r.order_id = ? "
                + "and r.status not in ('REJECTED','CANCELLED') group by rl.order_line_id, rl.warehouse_id", rs -> {
            byKey.computeIfAbsent(new Key(rs.getLong(1), rs.getLong(2)),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[1] = rs.getBigDecimal(3);
        }, orderId);
        return byKey.entrySet().stream().map(e -> new Returnable(e.getKey().orderLineId(), e.getKey().warehouseId(),
                e.getValue()[0], e.getValue()[1])).toList();
    }

    private static BigDecimal amountOf(RefundRequest req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw OrderService.invalid("amount", "Enter an amount.");
        }
        return OrderTotals.money(req.amount());
    }

    /** GOODS_RETURN: needs a dispatched order, and every line within what shipped from that warehouse. */
    private void addLines(Refund r, SalesOrder o, RefundRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw OrderService.invalid("lines", "Choose at least one item to return.");
        }
        if (o.status != OrderStatus.RELEASED && o.status != OrderStatus.FULFILLED) {
            throw new DomainException(ErrorCode.OVERPAYMENT,
                    "Goods can only be returned on a released or fulfilled order.");
        }
        Map<Key, BigDecimal> returnable = new LinkedHashMap<>();
        returnable(o.id).forEach(t -> returnable.put(new Key(t.orderLineId(), t.warehouseId()),
                t.shipped().subtract(t.returned())));
        BigDecimal total = BigDecimal.ZERO;
        List<RefundLine> lines = new ArrayList<>();
        for (RefundLineRequest lr : req.lines()) {
            OrderLine ol = o.lines.stream().filter(l -> l.id == lr.orderLineId()).findFirst()
                    .orElseThrow(() -> OrderService.invalid("lines", "That item isn't on this order."));
            Key key = new Key(lr.orderLineId(), lr.warehouseId());
            BigDecimal left = returnable.getOrDefault(key, BigDecimal.ZERO).subtract(lr.qty());
            if (left.signum() < 0) {
                throw new DomainException(ErrorCode.OVERPAYMENT, "Can't return more than was shipped from that warehouse.",
                        Map.of("returnable", left.add(lr.qty())));
            }
            returnable.put(key, left);
            RefundLine l = new RefundLine();
            l.orderLineId = lr.orderLineId();
            l.warehouseId = lr.warehouseId();
            l.qty = lr.qty();
            l.disposition = lr.disposition();
            l.amount = lr.qty().multiply(ol.netTotal != null ? ol.netTotal : ol.lineTotal)
                    .divide(ol.qty, 2, RoundingMode.HALF_UP);
            total = total.add(l.amount);
            lines.add(l);
        }
        r.lines.addAll(lines);
        r.amount = total;
    }

    /** What can still go back: paid in, less already refunded, less refunds asked for or approved but not yet paid. */
    static BigDecimal refundable(SalesOrder o, BigDecimal open) {
        return o.settledAmount.subtract(o.refundedAmount).subtract(open);
    }

    private void requireWithinRefundable(SalesOrder o, BigDecimal amount, BigDecimal open) {
        BigDecimal refundable = refundable(o, open);
        if (amount.compareTo(refundable) > 0) {
            throw new DomainException(ErrorCode.OVERPAYMENT, "That is more than can still be refunded ("
                    + refundable.max(BigDecimal.ZERO).toPlainString() + ").",
                    Map.of("refundable", refundable.max(BigDecimal.ZERO)));
        }
    }

    RefundResponse toResponse(Refund r, SalesOrder o) {
        Map<Long, String> descriptions = new LinkedHashMap<>();
        o.lines.forEach(l -> descriptions.put(l.id, l.description));
        List<RefundResponse.Line> lines = r.lines.stream().map(l -> new RefundResponse.Line(l.orderLineId,
                descriptions.get(l.orderLineId), l.warehouseId, l.qty, l.disposition, l.amount)).toList();
        String name = users.displayNames(Set.of(r.requestedBy)).get(r.requestedBy);
        return new RefundResponse(r.id, r.orderId, r.kind, r.status, r.amount, r.reason, lines, r.method, r.reference,
                r.proofKey == null ? null : storage.presignedUrl(r.proofKey), r.paidOn, r.creditMemoNo, name,
                r.createdAt, r.version);
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Refund not found");
    }
}
