package ph.thecoffeejunkie.crm.sales;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    RefundService(RefundRepository repo, OrderAccess access, Approvals approvals, AuditLog audit, Users users,
                  StorageService storage, JdbcTemplate jdbc) {
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
        requireWithinRefundable(o, r.amount);
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
        // Approvals lets only the requester cancel; for anyone else the request stays pending and the
        // listener ignores its eventual decision because the refund is no longer REQUESTED.
        if (r.status == RefundStatus.REQUESTED && r.approvalRequestId != null && r.requestedBy == s.userId()) {
            approvals.cancel(r.approvalRequestId);
        }
        r.status = RefundStatus.CANCELLED;
        r.updatedAt = Instant.now();
        repo.saveAndFlush(r);
        audit.record(AuditAction.REFUND_CANCELLED, OrderService.ENTITY, o.id,
                "Cancelled the " + r.amount.toPlainString() + " refund", null);
        return toResponse(r, o);
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

    private void requireWithinRefundable(SalesOrder o, BigDecimal amount) {
        BigDecimal refundable = o.settledAmount.subtract(o.refundedAmount).subtract(repo.openAmount(o.id));
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
