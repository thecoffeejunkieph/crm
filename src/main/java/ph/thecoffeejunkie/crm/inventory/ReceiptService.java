package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.RoundingMode;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
class ReceiptService {

    static final String ENTITY = "STOCK_RECEIPT";
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");

    private final StockReceiptRepository receipts;
    private final StockLedger ledger;
    private final InventoryPermissions perms;
    private final Warehouses warehouses;
    private final Catalog catalog;
    private final Users users;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final CurrentActor actor;

    ReceiptService(StockReceiptRepository receipts, StockLedger ledger, InventoryPermissions perms,
                   Warehouses warehouses, Catalog catalog, Users users, DocumentCounters counters, AuditLog audit,
                   CurrentActor actor) {
        this.receipts = receipts;
        this.ledger = ledger;
        this.perms = perms;
        this.warehouses = warehouses;
        this.catalog = catalog;
        this.users = users;
        this.counters = counters;
        this.audit = audit;
        this.actor = actor;
    }

    ReceiptResponse receive(ReceiptRequest req) {
        perms.require(req.warehouseId(), true);
        warehouses.get(req.warehouseId()); // 404 for an unknown or archived warehouse
        Map<Long, ProductInfo> products = catalog.info(req.lines().stream().map(ReceiptRequest.ReceiptLineRequest::productId).toList());
        for (int i = 0; i < req.lines().size(); i++) {
            ProductInfo p = products.get(req.lines().get(i).productId());
            if (p == null || p.archived()) {
                String msg = p == null ? "Unknown product." : "This product is archived.";
                throw new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                        Map.of("errors", List.of(Map.of("field", "lines[" + i + "].productId", "message", msg))));
            }
        }
        StockReceipt r = new StockReceipt();
        r.number = counters.format("SR", "STOCK_RECEIPT");
        r.warehouseId = req.warehouseId();
        r.supplierName = req.supplierName().trim();
        r.reference = req.reference();
        r.receivedOn = req.receivedOn();
        r.notes = req.notes();
        r.createdBy = actor.scope().userId();
        for (ReceiptRequest.ReceiptLineRequest l : req.lines()) {
            StockReceiptLine line = new StockReceiptLine();
            line.receipt = r;
            line.productId = l.productId();
            line.qty = l.qty();
            r.lines.add(line);
        }
        receipts.saveAndFlush(r);
        // Lock levels in product order; each post may allocate the new stock to short reservations.
        r.lines.stream().sorted(Comparator.comparingLong(l -> l.productId)).forEach(l ->
                ledger.post(MovementType.RECEIPT, l.productId, r.warehouseId, l.qty, ENTITY, r.id, null, null));
        audit.record(AuditAction.STOCK_RECEIVED, ENTITY, r.id, "Received stock " + r.number, null);
        return toResponses(List.of(r)).get(0);
    }

    @Transactional(readOnly = true)
    PageResponse<ReceiptResponse> list(Long warehouseId, PageQuery page) {
        perms.require(warehouseId, true);
        var pageable = page.toPageable(Set.of(), NEWEST);
        Page<StockReceipt> p = warehouseId == null ? receipts.findAll(pageable)
                : receipts.findByWarehouseId(warehouseId, pageable);
        return new PageResponse<>(p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements(),
                toResponses(p.getContent()));
    }

    @Transactional(readOnly = true)
    ReceiptResponse get(long id) {
        StockReceipt r = receipts.findById(id).filter(x -> perms.canAct(x.warehouseId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Receipt not found"));
        return toResponses(List.of(r)).get(0);
    }

    private List<ReceiptResponse> toResponses(Collection<StockReceipt> rs) {
        Map<Long, ProductInfo> products = catalog.info(rs.stream().flatMap(r -> r.lines.stream())
                .map(l -> l.productId).toList());
        Map<Long, String> names = users.displayNames(rs.stream().map(r -> r.createdBy).filter(Objects::nonNull).toList());
        return rs.stream().map(r -> new ReceiptResponse(r.id, r.number, r.warehouseId, r.supplierName, r.reference,
                r.receivedOn, r.notes, names.get(r.createdBy), r.createdAt, r.lines.stream().map(l -> {
                    ProductInfo p = products.get(l.productId);
                    return new ReceiptResponse.ReceiptLineResponse(l.productId, p.sku(), p.name(), p.unit(),
                            l.qty.setScale(3, RoundingMode.UNNECESSARY));
                }).toList())).toList();
    }
}
