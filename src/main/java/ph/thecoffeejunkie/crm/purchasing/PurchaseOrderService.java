package ph.thecoffeejunkie.crm.purchasing;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.inventory.InventoryPermissions;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers, InventoryPermissions perms,
                         Warehouses warehouses, Catalog catalog, DocumentCounters counters, AuditLog audit,
                         CurrentActor actor) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.perms = perms;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.counters = counters;
        this.audit = audit;
        this.actor = actor;
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
        po.cancel();
        orders.saveAndFlush(po);
        audit.record(AuditAction.CANCELLED, ENTITY, id, "Cancelled purchase order " + po.number, null);
        return toResponses(List.of(po)).get(0);
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
        Map<Long, ProductInfo> products = catalog.info(req.lines().stream().map(PoRequest.Line::productId).toList());
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
        for (PoRequest.Line l : req.lines()) {
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
                    return new PoResponse.LineResponse(l.id, l.productId, i.sku(), i.name(), l.qty, l.unitCost,
                            l.receivedQty);
                }).toList(), p.version)).toList();
    }
}
