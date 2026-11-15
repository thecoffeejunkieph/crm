package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.approvals.ApprovalResponse;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.CreateApprovalRequest;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
class AdjustmentService {

    static final String ENTITY = "STOCK_ADJUSTMENT";
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");

    private final StockAdjustmentRepository adjustments;
    private final StockLedger ledger;
    private final InventoryPermissions perms;
    private final Warehouses warehouses;
    private final Catalog catalog;
    private final Users users;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final Approvals approvals;
    private final Settings settings;
    private final Notifications notifications;

    AdjustmentService(StockAdjustmentRepository adjustments, StockLedger ledger, InventoryPermissions perms,
                      Warehouses warehouses, Catalog catalog, Users users, DocumentCounters counters, AuditLog audit,
                      CurrentActor actor, Approvals approvals, Settings settings, Notifications notifications) {
        this.notifications = notifications;
        this.adjustments = adjustments;
        this.ledger = ledger;
        this.perms = perms;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.users = users;
        this.counters = counters;
        this.audit = audit;
        this.actor = actor;
        this.approvals = approvals;
        this.settings = settings;
    }

    AdjustmentResponse create(AdjustmentRequest req) {
        perms.require(req.warehouseId(), true);
        warehouses.get(req.warehouseId());
        ProductInfo product = catalog.info(List.of(req.productId())).get(req.productId());
        if (product == null) {
            throw invalid("productId", "Unknown product.");
        }
        if (req.mode() == AdjustmentMode.COUNTED && req.countedQty() == null) {
            throw invalid("countedQty", "Required for a count.");
        }
        if (req.mode() == AdjustmentMode.DELTA && (req.delta() == null || req.delta().signum() == 0)) {
            throw invalid("delta", "Must be non-zero.");
        }
        StockLevel level = ledger.lock(req.productId(), req.warehouseId());
        BigDecimal delta = req.mode() == AdjustmentMode.COUNTED ? req.countedQty().subtract(level.onHand) : req.delta();
        delta = delta.setScale(3, RoundingMode.UNNECESSARY);
        if (level.onHand.add(delta).signum() < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_STOCK, "Not enough stock for this change.");
        }
        BigDecimal cost = product.cost() == null ? BigDecimal.ZERO : product.cost();
        BigDecimal value = delta.abs().multiply(cost);
        boolean pending = delta.signum() < 0
                && value.compareTo(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_VALUE_MAX)) > 0;

        StockAdjustment a = new StockAdjustment();
        a.number = counters.format("ADJ", ENTITY);
        a.warehouseId = req.warehouseId();
        a.productId = req.productId();
        a.mode = req.mode();
        a.countedQty = req.mode() == AdjustmentMode.COUNTED ? req.countedQty() : null;
        a.delta = delta;
        a.reason = req.reason().trim();
        a.createdBy = actor.scope().userId();
        a.status = pending ? AdjustmentStatus.PENDING_APPROVAL : AdjustmentStatus.POSTED;
        adjustments.saveAndFlush(a);
        if (pending) {
            // Approvals runs its template in this transaction (REQUIRED), so the row and the request commit or
            // roll back together; the adjustment is saved first because the request needs its id.
            ApprovalResponse ar = approvals.requestOnBehalf(new CreateApprovalRequest(ApprovalType.STOCK_WRITE_OFF,
                    ENTITY, a.id, 0, Map.of("qty", delta.abs(), "value", value), a.reason), a.createdBy);
            a.approvalRequestId = ar.id();
        } else {
            post(a, delta);
        }
        return toResponses(List.of(a)).get(0);
    }

    /** Called when the write-off approval is decided. */
    void decided(long id, boolean approved) {
        StockAdjustment a = adjustments.findByIdForUpdate(id).orElse(null);
        if (a == null || a.status != AdjustmentStatus.PENDING_APPROVAL) {
            return;
        }
        if (!approved) {
            a.status = AdjustmentStatus.REJECTED;
            a.updatedAt = Instant.now();
            return;
        }
        StockLevel level = ledger.lock(a.productId, a.warehouseId);
        BigDecimal delta = a.delta;
        if (a.mode == AdjustmentMode.COUNTED) {
            delta = a.countedQty.subtract(level.onHand).setScale(3, RoundingMode.UNNECESSARY);
            a.delta = delta;
        }
        if (level.onHand.add(delta).signum() < 0) {
            // Checked here instead of letting the ledger throw: an exception would mark this transaction
            // rollback-only and the rejection below would never commit.
            a.status = AdjustmentStatus.REJECTED;
            a.reason = "Could not post: insufficient stock (on hand " + level.onHand.stripTrailingZeros().toPlainString()
                    + ", change " + delta.stripTrailingZeros().toPlainString() + ")";
            a.updatedAt = Instant.now();
            audit.record(AuditAction.STOCK_ADJUSTED, ENTITY, a.id, "Rejected " + a.number + ": " + a.reason, null);
            if (a.createdBy != null) {
                notifications.toUser(a.createdBy, NotificationType.APPROVAL_DECIDED, ENTITY, a.id,
                        "Adjustment " + a.number + " could not be posted", a.reason);
            }
            return;
        }
        post(a, delta);
    }

    /** Always POSTED, no approval. TRANSIT_LOSS is recorded only: the stock already left with the transfer. */
    long postSystem(long warehouseId, long productId, BigDecimal delta, AdjustmentSource source, String reason,
                    String refType, long refId) {
        StockAdjustment a = new StockAdjustment();
        a.number = counters.format("ADJ", ENTITY);
        a.warehouseId = warehouseId;
        a.productId = productId;
        a.mode = AdjustmentMode.DELTA;
        a.delta = delta;
        a.reason = reason;
        a.source = source;
        a.createdBy = actor.staff().map(StaffPrincipal::userId).orElse(null);
        a.status = AdjustmentStatus.POSTED;
        adjustments.saveAndFlush(a);
        if (source != AdjustmentSource.TRANSIT_LOSS) {
            post(a, delta);
        } else {
            audit.record(AuditAction.STOCK_ADJUSTED, ENTITY, a.id,
                    "Recorded " + a.number + " from " + refType + " #" + refId, null);
        }
        return a.id;
    }

    private void post(StockAdjustment a, BigDecimal delta) {
        if (delta.signum() != 0) {
            ledger.post(MovementType.ADJUSTMENT, a.productId, a.warehouseId, delta, ENTITY, a.id, a.reason, null);
        }
        a.status = AdjustmentStatus.POSTED;
        a.updatedAt = Instant.now();
        audit.record(AuditAction.STOCK_ADJUSTED, ENTITY, a.id, "Adjusted stock " + a.number, null);
    }

    @Transactional(readOnly = true)
    PageResponse<AdjustmentResponse> list(Long warehouseId, AdjustmentStatus status, PageQuery page) {
        perms.require(warehouseId, true);
        Specification<StockAdjustment> spec = Specification.allOf(
                (r, q, cb) -> warehouseId == null ? null : cb.equal(r.get("warehouseId"), warehouseId),
                (r, q, cb) -> status == null ? null : cb.equal(r.get("status"), status));
        Page<StockAdjustment> p = adjustments.findAll(spec, page.toPageable(Set.of(), NEWEST));
        return new PageResponse<>(p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements(),
                toResponses(p.getContent()));
    }

    private List<AdjustmentResponse> toResponses(Collection<StockAdjustment> rs) {
        Map<Long, ProductInfo> products = catalog.info(rs.stream().map(a -> a.productId).toList());
        Map<Long, String> names = users.displayNames(rs.stream().map(a -> a.createdBy).filter(Objects::nonNull).toList());
        return rs.stream().map(a -> {
            ProductInfo p = products.get(a.productId);
            return new AdjustmentResponse(a.id, a.number, a.status, a.warehouseId, a.productId, p.sku(), p.name(),
                    p.unit(), a.mode, a.countedQty, a.delta, a.reason, a.source, names.get(a.createdBy), a.createdAt);
        }).toList();
    }

    private static DomainException invalid(String field, String msg) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                Map.of("errors", List.of(Map.of("field", field, "message", msg))));
    }
}
