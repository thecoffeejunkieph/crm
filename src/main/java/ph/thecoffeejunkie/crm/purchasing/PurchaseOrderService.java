package ph.thecoffeejunkie.crm.purchasing;

import jakarta.persistence.criteria.Predicate;
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
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.InventoryPermissions;
import ph.thecoffeejunkie.crm.inventory.ReceiptRequest;
import ph.thecoffeejunkie.crm.inventory.ReceiptResponse;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Scope;
import ph.thecoffeejunkie.crm.shared.SortRequest;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
class PurchaseOrderService {

    static final String ENTITY = "PURCHASE_ORDER";
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");
    private static final Set<String> SORTABLE = Set.of("number", "status", "expectedOn", "createdAt");

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final InventoryPermissions perms;
    private final Warehouses warehouses;
    private final Catalog catalog;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final CreditQuery credit;
    private final Approvals approvals;
    private final Inventory inventory;

    PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers, InventoryPermissions perms,
                         Warehouses warehouses, Catalog catalog, DocumentCounters counters, AuditLog audit,
                         CurrentActor actor, CreditQuery credit, Approvals approvals, Inventory inventory) {
        this.inventory = inventory;
        this.orders = orders;
        this.suppliers = suppliers;
        this.perms = perms;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.counters = counters;
        this.audit = audit;
        this.actor = actor;
        this.credit = credit;
        this.approvals = approvals;
    }

    PoResponse createDraft(PoRequest req) {
        requireAdmin();
        PurchaseOrder po = new PurchaseOrder();
        po.number = counters.format("PO", "PURCHASE_ORDER");
        po.createdBy = actor.scope().userId();
        apply(po, req);
        orders.saveAndFlush(po);
        audit.record(AuditAction.CREATED, ENTITY, po.id, "Created purchase order " + po.number, null);
        return toResponses(List.of(po)).get(0);
    }

    PoResponse update(long id, PoRequest req) {
        requireAdmin();
        PurchaseOrder po = orders.lockById(id).orElseThrow(PurchaseOrderService::notFound);
        po.requireEditable();
        apply(po, req);
        po.updatedAt = Instant.now();
        orders.saveAndFlush(po);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Edited purchase order " + po.number, null);
        return toResponses(List.of(po)).get(0);
    }

    PoResponse cancel(long id) {
        requireAdmin();
        PurchaseOrder po = orders.lockById(id).orElseThrow(PurchaseOrderService::notFound);
        boolean pending = po.status == PoStatus.PENDING_APPROVAL;
        po.cancel();
        if (pending && po.approvalRequestId != null) {
            approvals.cancelForSubject(po.approvalRequestId);
        }
        orders.saveAndFlush(po);
        audit.record(AuditAction.CANCELLED, ENTITY, id, "Cancelled purchase order " + po.number, null);
        return toResponses(List.of(po)).get(0);
    }

    PoResponse send(long id) {
        requireAdmin();
        PurchaseOrder po = orders.lockById(id).orElseThrow(PurchaseOrderService::notFound);
        po.requireStatus(PoStatus.DRAFT);
        if (po.lines.isEmpty()) {
            throw invalid("lines", "Purchase order " + po.number + " has no lines.");
        }
        Supplier supplier = suppliers.findById(po.supplierId).orElseThrow();
        if (supplier.archivedAt != null) {
            throw invalid("supplierId", "This supplier is archived.");
        }
        CreditCheck check = check(po);
        po.rejectionReason = null;
        po.updatedAt = Instant.now();
        if (!check.needsApproval()) {
            markSent(po);
            orders.saveAndFlush(po);
            audit.record(AuditAction.UPDATED, ENTITY, id, "Sent purchase order " + po.number, null);
            return toResponses(List.of(po)).get(0);
        }
        String supplierName = supplier.name;
        po.status = PoStatus.PENDING_APPROVAL;
        orders.saveAndFlush(po);
        // Approvals runs in this transaction (REQUIRED); the PO is saved first so the version is the one the request names.
        ApprovalResponse ar = approvals.requestOnBehalf(new CreateApprovalRequest(ApprovalType.PO_OVER_CREDIT, ENTITY,
                po.id, po.version, Map.of("total", po.total(), "over", check.over(), "supplier", supplierName,
                "number", po.number), "PO " + po.number + " exceeds credit with " + supplierName + " by " + check.over()),
                actor.scope().userId());
        po.approvalRequestId = ar.id();
        orders.saveAndFlush(po);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Purchase order " + po.number + " sent for approval (over credit)", null);
        return toResponses(List.of(po)).get(0);
    }

    /**
     * Receives into the PO's warehouse. The PO row lock serialises receipts, so two at once cannot both pass the
     * remaining check; the stock receipt and received_qty commit or roll back together, keeping received_qty equal
     * to the PO's receipt lines (credit and the supplier statement both rely on that).
     */
    PoResponse receive(long id, PoReceiveRequest req) {
        PurchaseOrder po = orders.lockById(id).filter(x -> perms.canAct(x.warehouseId))
                .orElseThrow(PurchaseOrderService::notFound);
        po.requireStatus(PoStatus.SENT, PoStatus.PARTIALLY_RECEIVED);
        Map<Long, PurchaseOrderLine> byId = po.lines.stream().collect(Collectors.toMap(l -> l.id, Function.identity()));
        Set<Long> seen = new HashSet<>();
        List<ReceiptRequest.ReceiptLineRequest> receiptLines = new ArrayList<>();
        for (int i = 0; i < req.lines().size(); i++) {
            PoReceiveRequest.PoReceiveLine r = req.lines().get(i);
            PurchaseOrderLine line = byId.get(r.lineId());
            if (line == null) {
                throw invalid("lines[" + i + "].lineId", "Not a line of this purchase order.");
            }
            if (!seen.add(r.lineId())) {
                throw invalid("lines[" + i + "].lineId", "This line is already in the receipt.");
            }
            BigDecimal remaining = line.qty.subtract(line.receivedQty);
            if (r.qty().compareTo(remaining) > 0) {
                ProductInfo p = catalog.info(List.of(line.productId)).get(line.productId);
                throw new DomainException(ErrorCode.CONFLICT, "Purchase order " + po.number + ", line " + p.sku()
                        + ": only " + remaining.stripTrailingZeros().toPlainString() + " left to receive.");
            }
            receiptLines.add(new ReceiptRequest.ReceiptLineRequest(line.productId, r.qty()));
        }
        String supplierName = suppliers.findById(po.supplierId).orElseThrow().name;
        ReceiptResponse receipt = inventory.receiveForPurchase(new ReceiptRequest(po.warehouseId, supplierName,
                req.reference(), req.receivedOn(), "PO " + po.number, receiptLines), po.id);
        req.lines().forEach(r -> {
            PurchaseOrderLine line = byId.get(r.lineId());
            line.receivedQty = line.receivedQty.add(r.qty());
        });
        po.status = po.lines.stream().allMatch(l -> l.receivedQty.compareTo(l.qty) >= 0)
                ? PoStatus.RECEIVED : PoStatus.PARTIALLY_RECEIVED;
        po.updatedAt = Instant.now();
        orders.saveAndFlush(po);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Received " + receipt.number() + " against purchase order "
                + po.number, null);
        return toResponses(List.of(po)).get(0);
    }

    /** Ends a partly received PO: what is still open no longer counts as committed credit. */
    PoResponse closeShort(long id) {
        requireAdmin();
        PurchaseOrder po = orders.lockById(id).orElseThrow(PurchaseOrderService::notFound);
        po.requireStatus(PoStatus.PARTIALLY_RECEIVED);
        po.status = PoStatus.RECEIVED;
        po.closedShort = true;
        po.updatedAt = Instant.now();
        orders.saveAndFlush(po);
        audit.record(AuditAction.UPDATED, ENTITY, id, "Closed purchase order " + po.number + " short", null);
        return toResponses(List.of(po)).get(0);
    }

    @Transactional(readOnly = true)
    CreditCheck creditCheck(long id) {
        requireAdmin();
        return check(orders.findById(id).orElseThrow(PurchaseOrderService::notFound));
    }

    /** Exposure = balance + committed + this PO total; a draft is not yet in committed. Exactly at the limit is within it. */
    private CreditCheck check(PurchaseOrder po) {
        SupplierCredit c = credit.forSuppliers(List.of(po.supplierId)).get(po.supplierId);
        BigDecimal exposure = c.balance().add(c.committed()).add(po.total());
        if (c.limit() == null) {
            return new CreditCheck(exposure, null, BigDecimal.ZERO, false);
        }
        BigDecimal over = exposure.subtract(c.limit()).max(BigDecimal.ZERO);
        return new CreditCheck(exposure, c.limit(), over, over.signum() > 0);
    }

    private static void markSent(PurchaseOrder po) {
        po.status = PoStatus.SENT;
        po.sentAt = Instant.now();
    }

    /** Approval outcome. Only a PO still waiting changes, so a cancelled or edited one is never resurrected. */
    void decided(long poId, boolean approved, String reason) {
        PurchaseOrder po = orders.lockById(poId).orElse(null);
        if (po == null || po.status != PoStatus.PENDING_APPROVAL) {
            return;
        }
        po.updatedAt = Instant.now();
        if (approved) {
            markSent(po);
            po.rejectionReason = null;
        } else {
            po.status = PoStatus.DRAFT;
            po.rejectionReason = reason;
            po.approvalRequestId = null;
        }
        orders.saveAndFlush(po);
        audit.record(AuditAction.UPDATED, ENTITY, poId, "Purchase order " + po.number
                + (approved ? " approved and sent" : " approval rejected; back to draft"), null);
    }

    @Transactional(readOnly = true)
    PoResponse get(long id) {
        PurchaseOrder po = orders.findById(id).filter(x -> perms.canRead(x.warehouseId))
                .orElseThrow(PurchaseOrderService::notFound);
        return toResponses(List.of(po)).get(0);
    }

    @Transactional(readOnly = true)
    PageResponse<PoResponse> list(PoStatus status, Long supplierId, PageQuery page) {
        Scope scope = actor.scope();
        Specification<PurchaseOrder> spec = (r, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(r.get("status"), status));
            }
            if (supplierId != null) {
                ps.add(cb.equal(r.get("supplierId"), supplierId));
            }
            if (!scope.isAdmin()) { // same rule as canRead for the roles that reach this endpoint
                ps.add(scope.warehouseIds().isEmpty() ? cb.disjunction() : r.get("warehouseId").in(scope.warehouseIds()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var sort = SortRequest.<PurchaseOrder>of(page, SORTABLE, Map.of(), NEWEST);
        var p = orders.findAll(spec.and(sort.orderSpec()), sort.pageable());
        return new PageResponse<>(p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements(),
                toResponses(p.getContent()));
    }

    private void requireAdmin() {
        if (!actor.scope().isAdmin()) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only an admin can change purchase orders.");
        }
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Purchase order not found");
    }

    private void apply(PurchaseOrder po, PoRequest req) {
        Supplier s = suppliers.findById(req.supplierId()).orElse(null);
        if (s == null || s.archivedAt != null) {
            throw invalid("supplierId", s == null ? "Unknown supplier." : "This supplier is archived.");
        }
        long warehouseId = req.warehouseId() == null ? warehouses.defaultId() : req.warehouseId();
        warehouses.get(warehouseId); // 404 for an unknown or archived warehouse
        Map<Long, ProductInfo> products = catalog.info(req.lines().stream().map(PoRequest.PoLineRequest::productId).toList());
        Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < req.lines().size(); i++) {
            if (!seen.add(req.lines().get(i).productId())) {
                throw invalid("lines[" + i + "].productId", "This product is already on the PO.");
            }
            ProductInfo p = products.get(req.lines().get(i).productId());
            if (p == null || p.archived()) {
                throw invalid("lines[" + i + "].productId", p == null ? "Unknown product." : "This product is archived.");
            }
        }
        po.supplierId = s.id;
        po.warehouseId = warehouseId;
        po.expectedOn = req.expectedOn();
        po.notes = req.notes();
        po.lines.clear();
        orders.flush(); // delete the old lines first: UNIQUE (po_id, product_id) would trip on re-inserting the same product
        for (PoRequest.PoLineRequest l : req.lines()) {
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.po = po;
            line.productId = l.productId();
            line.qty = l.qty();
            BigDecimal cost = l.unitCost() != null ? l.unitCost() : products.get(l.productId()).cost();
            line.unitCost = (cost == null ? BigDecimal.ZERO : cost).setScale(2, RoundingMode.HALF_UP);
            po.lines.add(line);
        }
    }

    private static DomainException invalid(String field, String msg) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                Map.of("errors", List.of(Map.of("field", field, "message", msg))));
    }

    private List<PoResponse> toResponses(Collection<PurchaseOrder> pos) {
        Map<Long, ProductInfo> products = catalog.info(pos.stream().flatMap(p -> p.lines.stream())
                .map(l -> l.productId).toList());
        Map<Long, String> names = suppliers.findAllById(pos.stream().map(p -> p.supplierId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(s -> s.id, s -> s.name));
        return pos.stream().map(p -> new PoResponse(p.id, p.number, p.supplierId, names.get(p.supplierId),
                p.warehouseId, p.status, p.expectedOn, p.notes, p.rejectionReason, p.approvalRequestId, p.sentAt,
                p.closedShort, p.total(), p.lines.stream().map(l -> {
                    ProductInfo i = products.get(l.productId);
                    return new PoResponse.PoLineResponse(l.id, l.productId, i.sku(), i.name(), l.qty, l.unitCost,
                            l.receivedQty);
                }).toList(), p.version)).toList();
    }
}
