package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.Like;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stock transfers (spec section 4). The row is locked before the state check so a double tap cannot post twice. */
@Service
@Transactional
class TransferService {

    static final String ENTITY = "TRANSFER";
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");

    private final StockTransferRepository transfers;
    private final StockLedger ledger;
    private final InventoryPermissions perms;
    private final Warehouses warehouses;
    private final Catalog catalog;
    private final Users users;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final Notifications notifications;
    private final AdjustmentService adjustments;

    TransferService(StockTransferRepository transfers, StockLedger ledger, InventoryPermissions perms,
                    Warehouses warehouses, Catalog catalog, Users users, DocumentCounters counters, AuditLog audit,
                    CurrentActor actor, Notifications notifications, AdjustmentService adjustments) {
        this.transfers = transfers;
        this.ledger = ledger;
        this.perms = perms;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.users = users;
        this.counters = counters;
        this.audit = audit;
        this.actor = actor;
        this.notifications = notifications;
        this.adjustments = adjustments;
    }

    TransferResponse create(TransferRequest req) {
        if (!perms.canAct(req.fromWarehouseId())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
        }
        StockTransfer t = new StockTransfer();
        t.createdBy = actor.scope().userId();
        return toResponse(save(t, req));
    }

    /** For cover-shortage: no warehouse scope check, the caller (sales) acts on its own order. */
    TransferResponse createLinked(long from, long to, long orderId, List<ReserveLine> lines, long createdBy) {
        StockTransfer t = new StockTransfer();
        t.createdBy = createdBy;
        return toResponse(save(t, new TransferRequest(from, to, orderId, null, null, lines.stream()
                .map(l -> new TransferRequest.TransferLineRequest(l.productId(), l.qty())).toList())));
    }

    TransferResponse update(long id, TransferRequest req) {
        if (req.version() == null) {
            throw invalid("version", "must not be null");
        }
        StockTransfer t = lock(id, true);
        if (!perms.canAct(t.fromWarehouseId) || !perms.canAct(req.fromWarehouseId())) {
            throw notFound();
        }
        if (t.version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION,
                    "This record was changed by someone else. Reload and retry.");
        }
        requireDraft(t);
        t.lines.clear();
        transfers.flush();
        return toResponse(save(t, req));
    }

    private StockTransfer save(StockTransfer t, TransferRequest req) {
        if (req.fromWarehouseId().equals(req.toWarehouseId())) {
            throw invalid("toWarehouseId", "Must differ from the source warehouse.");
        }
        warehouses.get(req.fromWarehouseId());
        warehouses.get(req.toWarehouseId());
        Map<Long, ProductInfo> products = catalog.info(req.lines().stream()
                .map(TransferRequest.TransferLineRequest::productId).toList());
        for (int i = 0; i < req.lines().size(); i++) {
            ProductInfo p = products.get(req.lines().get(i).productId());
            if (p == null || p.archived()) {
                throw invalid("lines[" + i + "].productId", p == null ? "Unknown product." : "This product is archived.");
            }
        }
        boolean isNew = t.id == null;
        if (isNew) {
            t.number = counters.format("TR", "STOCK_TRANSFER");
        }
        t.fromWarehouseId = req.fromWarehouseId();
        t.toWarehouseId = req.toWarehouseId();
        t.salesOrderId = req.salesOrderId();
        t.notes = req.notes();
        t.updatedAt = Instant.now();
        for (TransferRequest.TransferLineRequest l : req.lines()) {
            StockTransferLine line = new StockTransferLine();
            line.transfer = t;
            line.productId = l.productId();
            line.qty = l.qty();
            t.lines.add(line);
        }
        transfers.saveAndFlush(t);
        audit.record(isNew ? AuditAction.TRANSFER_CREATED : AuditAction.UPDATED, ENTITY, t.id,
                (isNew ? "Created transfer " : "Updated transfer ") + t.number, null);
        return t;
    }

    TransferResponse dispatch(long id) {
        StockTransfer t = lock(id, true);
        if (!perms.canAct(t.fromWarehouseId)) {
            throw notFound();
        }
        requireDraft(t);
        // Level locks are taken in product order; a short level makes the ledger throw INSUFFICIENT_STOCK.
        t.lines.stream().sorted(Comparator.comparingLong(l -> l.productId)).forEach(l ->
                ledger.post(MovementType.TRANSFER_OUT, l.productId, t.fromWarehouseId, l.qty.negate(), ENTITY, t.id,
                        t.number, null));
        t.status = TransferStatus.DISPATCHED;
        t.dispatchedAt = Instant.now();
        t.dispatchedBy = actor.scope().userId();
        t.updatedAt = t.dispatchedAt;
        audit.record(AuditAction.TRANSFER_DISPATCHED, ENTITY, t.id, "Dispatched transfer " + t.number, null);
        for (long userId : users.idsForWarehouse(t.toWarehouseId)) {
            notifications.toUser(userId, NotificationType.TRANSFER_INBOUND, ENTITY, t.id,
                    "Transfer " + t.number + " is on its way", t.lines.size() + " line(s) to receive.");
        }
        return toResponse(t);
    }

    TransferResponse receive(long id, ReceiveRequest req) {
        StockTransfer t = lock(id, true);
        if (!perms.canAct(t.toWarehouseId)) {
            throw notFound();
        }
        if (t.status != TransferStatus.DISPATCHED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Only a dispatched transfer can be received.");
        }
        Map<Long, StockTransferLine> byId = t.lines.stream().collect(Collectors.toMap(l -> l.id, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < req.lines().size(); i++) {
            ReceiveRequest.ReceiveLineRequest r = req.lines().get(i);
            StockTransferLine line = byId.get(r.lineId());
            if (line == null || !seen.add(r.lineId())) {
                throw invalid("lines[" + i + "].lineId", "Not a line of this transfer, or repeated.");
            }
            if (r.receivedQty().compareTo(line.qty) > 0) {
                throw invalid("lines[" + i + "].receivedQty", "Cannot exceed the dispatched quantity.");
            }
            line.receivedQty = r.receivedQty().setScale(3, RoundingMode.UNNECESSARY);
        }
        if (seen.size() != byId.size()) {
            throw invalid("lines", "Every line of the transfer must be received.");
        }
        // Every TRANSFER_IN (level locks, in product order) before any TRANSIT_LOSS takes the adjustment counter,
        // so a concurrent short pick or receive can never hold that counter while waiting on one of these levels.
        List<StockTransferLine> sorted = t.lines.stream().sorted(Comparator.comparingLong(l -> l.productId)).toList();
        sorted.forEach(l -> {
            if (l.receivedQty.signum() > 0) {
                ledger.post(MovementType.TRANSFER_IN, l.productId, t.toWarehouseId, l.receivedQty, ENTITY, t.id,
                        t.number, t.salesOrderId);
            }
        });
        sorted.forEach(l -> {
            BigDecimal gap = l.qty.subtract(l.receivedQty);
            if (gap.signum() > 0) {
                adjustments.postSystem(t.fromWarehouseId, l.productId, gap.negate(), AdjustmentSource.TRANSIT_LOSS,
                        "Transit loss on " + t.number, ENTITY, t.id);
            }
        });
        t.status = TransferStatus.RECEIVED;
        t.receivedAt = Instant.now();
        t.receivedBy = actor.scope().userId();
        t.updatedAt = t.receivedAt;
        audit.record(AuditAction.TRANSFER_RECEIVED, ENTITY, t.id, "Received transfer " + t.number, null);
        return toResponse(t);
    }

    TransferResponse cancel(long id) {
        StockTransfer t = lock(id, true);
        requireDraft(t);
        t.status = TransferStatus.CANCELLED;
        t.updatedAt = Instant.now();
        audit.record(AuditAction.TRANSFER_CANCELLED, ENTITY, t.id, "Cancelled transfer " + t.number, null);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    PageResponse<TransferResponse> list(TransferStatus status, Long warehouseId, String q, PageQuery page) {
        if (warehouseId != null && !perms.canRead(warehouseId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
        }
        StaffPrincipal me = actor.staff().orElseThrow();
        Specification<StockTransfer> spec = Specification.allOf(
                (r, query, cb) -> status == null ? null : cb.equal(r.get("status"), status),
                (r, query, cb) -> Like.blank(q) ? null : cb.like(cb.lower(r.get("number")), Like.contains(q), '\\'),
                (r, query, cb) -> warehouseId == null ? null
                        : cb.or(cb.equal(r.get("fromWarehouseId"), warehouseId),
                                cb.equal(r.get("toWarehouseId"), warehouseId)),
                (r, query, cb) -> me.role() != Role.WAREHOUSE ? null
                        : me.warehouseIds().isEmpty() ? cb.disjunction()
                        : cb.or(r.get("fromWarehouseId").in(me.warehouseIds()),
                                r.get("toWarehouseId").in(me.warehouseIds())));
        Page<StockTransfer> p = transfers.findAll(spec, page.toPageable(Set.of(), NEWEST));
        return new PageResponse<>(p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements(),
                toResponses(p.getContent()));
    }

    @Transactional(readOnly = true)
    TransferResponse get(long id) {
        return toResponse(lock(id, false));
    }

    /** Loads the transfer (row-locked when it will change); one the caller cannot see at either end is a 404. */
    private StockTransfer lock(long id, boolean forUpdate) {
        var found = forUpdate ? transfers.findByIdForUpdate(id) : transfers.findById(id);
        return found.filter(t -> forUpdate ? perms.canAct(t.fromWarehouseId) || perms.canAct(t.toWarehouseId)
                        : perms.canRead(t.fromWarehouseId) || perms.canRead(t.toWarehouseId))
                .orElseThrow(TransferService::notFound);
    }

    private static void requireDraft(StockTransfer t) {
        if (t.status != TransferStatus.DRAFT) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Only a draft transfer can be changed.");
        }
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Transfer not found");
    }

    private static DomainException invalid(String field, String msg) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                Map.of("errors", List.of(Map.of("field", field, "message", msg))));
    }

    private TransferResponse toResponse(StockTransfer t) {
        return toResponses(List.of(t)).get(0);
    }

    private List<TransferResponse> toResponses(Collection<StockTransfer> ts) {
        Map<Long, ProductInfo> products = catalog.info(ts.stream().flatMap(t -> t.lines.stream())
                .map(l -> l.productId).toList());
        Map<Long, String> names = users.displayNames(ts.stream().map(t -> t.createdBy).filter(Objects::nonNull).toList());
        return ts.stream().map(t -> new TransferResponse(t.id, t.number, t.status, t.fromWarehouseId, t.toWarehouseId,
                t.salesOrderId, t.notes, t.version, names.get(t.createdBy), t.createdAt, t.dispatchedAt, t.receivedAt,
                t.lines.stream().map(l -> {
                    ProductInfo p = products.get(l.productId);
                    return new TransferResponse.TransferLineResponse(l.id, l.productId, p.sku(), p.name(), p.unit(),
                            l.qty.setScale(3, RoundingMode.UNNECESSARY),
                            l.receivedQty == null ? null : l.receivedQty.setScale(3, RoundingMode.UNNECESSARY));
                }).toList())).toList();
    }
}
