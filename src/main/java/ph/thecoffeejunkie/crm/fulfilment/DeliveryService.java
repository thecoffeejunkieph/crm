package ph.thecoffeejunkie.crm.fulfilment;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pick, dispatch and deliver (spec section 4). Every action locks the row before its state check, so a double tap
 * serialises and the second one sees the new state. Out of scope is a 404.
 */
@Service
@Transactional
class DeliveryService {

    static final String ENTITY = "DELIVERY_ORDER";
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");

    private final DeliveryOrderRepository orders;
    private final Inventory inventory;
    private final StorageService storage;
    private final Catalog catalog;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final ApplicationEventPublisher events;

    DeliveryService(DeliveryOrderRepository orders, Inventory inventory, StorageService storage, Catalog catalog,
                    AuditLog audit, CurrentActor actor, ApplicationEventPublisher events) {
        this.orders = orders;
        this.inventory = inventory;
        this.storage = storage;
        this.catalog = catalog;
        this.audit = audit;
        this.actor = actor;
        this.events = events;
    }

    @Transactional(readOnly = true)
    PageResponse<DeliveryOrderResponse> list(DeliveryStatus status, Long warehouseId, PageQuery page) {
        Scope me = actor.scope();
        if (warehouseId != null && me.role() == Role.WAREHOUSE && !me.warehouseIds().contains(warehouseId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
        }
        Specification<DeliveryOrder> spec = Specification.allOf(
                (r, q, cb) -> status == null ? null : cb.equal(r.get("status"), status),
                (r, q, cb) -> warehouseId == null ? null : cb.equal(r.get("warehouseId"), warehouseId),
                (r, q, cb) -> switch (me.role()) {
                    case WAREHOUSE -> me.warehouseIds().isEmpty() ? cb.disjunction()
                            : r.get("warehouseId").in(me.warehouseIds());
                    case SALES -> cb.equal(r.get("repUserId"), me.userId());
                    default -> null;
                });
        Page<DeliveryOrder> p = orders.findAll(spec, page.toPageable(Set.of(), NEWEST));
        return new PageResponse<>(p.getNumber() + 1, p.getSize(), p.getTotalPages(), p.getTotalElements(),
                toResponses(p.getContent()));
    }

    @Transactional(readOnly = true)
    DeliveryOrderResponse get(long id) {
        return toResponse(orders.findById(id).filter(this::canRead).orElseThrow(DeliveryService::notFound));
    }

    DeliveryOrderResponse pick(long id, PickRequest req) {
        DeliveryOrder d = lock(id);
        if (d.status != DeliveryStatus.PICKING) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Only a delivery order being picked can be picked.");
        }
        Map<Long, DeliveryOrderLine> byId = d.lines.stream().collect(Collectors.toMap(l -> l.id, Function.identity()));
        Map<DeliveryOrderLine, BigDecimal> picked = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < req.lines().size(); i++) {
            PickRequest.PickLineRequest r = req.lines().get(i);
            DeliveryOrderLine line = byId.get(r.lineId());
            if (line == null || !seen.add(r.lineId())) {
                throw invalid("lines[" + i + "].lineId", "Not a line of this delivery order, or repeated.");
            }
            if (r.pickedQty().compareTo(line.qty) > 0) {
                throw invalid("lines[" + i + "].pickedQty", "Cannot exceed the ordered quantity.");
            }
            picked.put(line, r.pickedQty().setScale(3, RoundingMode.UNNECESSARY));
        }
        Map<Long, BigDecimal> gaps = new LinkedHashMap<>();
        picked.keySet().stream().sorted(Comparator.comparingLong((DeliveryOrderLine l) -> l.productId)
                .thenComparingLong(l -> l.orderLineId)).forEach(line -> {
            BigDecimal gap = (line.pickedQty == null ? line.qty : line.pickedQty).subtract(picked.get(line));
            if (gap.signum() > 0) {
                gaps.put(line.orderLineId, gap);
            }
            line.pickedQty = picked.get(line);
        });
        // One call, so every level is locked before the first adjustment number is taken.
        if (!gaps.isEmpty()) {
            inventory.shortPicks(gaps, d.number, d.id);
        }
        d.updatedAt = Instant.now();
        audit.record(AuditAction.PICKED, ENTITY, d.id, "Picked " + picked.size() + " line(s) on " + d.number, null);
        return toResponse(d);
    }

    DeliveryOrderResponse dispatch(long id) {
        DeliveryOrder d = lock(id);
        if (d.status != DeliveryStatus.PICKING || d.method != DeliveryMethod.DELIVERY) {
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Only a delivery being picked (not a pickup) can be dispatched.");
        }
        consume(d);
        d.status = DeliveryStatus.DISPATCHED;
        d.dispatchedAt = Instant.now();
        d.dispatchedBy = actor.scope().userId();
        d.updatedAt = d.dispatchedAt;
        audit.record(AuditAction.DISPATCHED, ENTITY, d.id, "Dispatched delivery order " + d.number, null);
        return toResponse(d);
    }

    /** The photo is stored only after every state check has passed, so a refused delivery stores nothing. */
    DeliveryOrderResponse deliver(long id, MultipartFile photo, String receiverName) {
        DeliveryOrder d = lock(id);
        if (photo == null || photo.isEmpty() || receiverName == null || receiverName.isBlank()) {
            throw new DomainException(ErrorCode.PROOF_REQUIRED, "A photo and the name of the receiver are required.");
        }
        if (d.method == DeliveryMethod.PICKUP && d.status == DeliveryStatus.PICKING) {
            consume(d);
        } else if (d.method != DeliveryMethod.DELIVERY || d.status != DeliveryStatus.DISPATCHED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This delivery order cannot be delivered now.");
        }
        // A bad photo throws 400 here and rolls back the pickup's stock postings with it.
        d.proofKey = storage.store("delivery-orders/" + d.id + "/proof", photo, StorageService.IMAGE_TYPES);
        d.receiverName = receiverName.trim();
        d.status = DeliveryStatus.DELIVERED;
        d.deliveredAt = Instant.now();
        d.deliveredBy = actor.scope().userId();
        d.updatedAt = d.deliveredAt;
        audit.record(AuditAction.DELIVERED, ENTITY, d.id,
                "Delivered " + d.number + ", received by " + d.receiverName, null);
        events.publishEvent(new OrderDelivered(d.salesOrderId, d.deliveredAt));
        return toResponse(d);
    }

    /** Every line fully picked, then the stock leaves (ORDER_SHORT while any reservation is short). */
    private void consume(DeliveryOrder d) {
        if (d.lines.stream().anyMatch(l -> l.pickedQty == null || l.pickedQty.compareTo(l.qty) != 0)) {
            throw new DomainException(ErrorCode.PICK_INCOMPLETE, "Pick every line in full first.");
        }
        inventory.consume(d.salesOrderId, d.number, d.id);
    }

    private DeliveryOrder lock(long id) {
        return orders.findByIdForUpdate(id).filter(this::canAct).orElseThrow(DeliveryService::notFound);
    }

    private boolean canRead(DeliveryOrder d) {
        Scope me = actor.scope();
        return switch (me.role()) {
            case ADMIN -> true;
            case WAREHOUSE -> me.warehouseIds().contains(d.warehouseId);
            case SALES -> d.repUserId == me.userId();
            default -> false;
        };
    }

    private boolean canAct(DeliveryOrder d) {
        Scope me = actor.scope();
        return me.role() == Role.ADMIN || me.role() == Role.WAREHOUSE && me.warehouseIds().contains(d.warehouseId);
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Delivery order not found");
    }

    private static DomainException invalid(String field, String msg) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                Map.of("errors", List.of(Map.of("field", field, "message", msg))));
    }

    private DeliveryOrderResponse toResponse(DeliveryOrder d) {
        return toResponses(List.of(d)).get(0);
    }

    private List<DeliveryOrderResponse> toResponses(Collection<DeliveryOrder> ds) {
        Map<Long, ProductInfo> products = catalog.info(ds.stream().flatMap(d -> d.lines.stream())
                .map(l -> l.productId).toList());
        return ds.stream().map(d -> new DeliveryOrderResponse(d.id, d.number, d.status, d.method, d.salesOrderId,
                d.orderNumber, d.warehouseId, d.customerName, d.shipTo, d.createdAt, d.dispatchedAt, d.deliveredAt,
                d.receiverName, StorageService.url(d.proofKey), d.lines.stream().map(l ->
                        new DeliveryOrderResponse.DeliveryLineResponse(l.id, l.orderLineId, l.productId,
                                products.get(l.productId).sku(), l.description, l.unit,
                                l.qty.setScale(3, RoundingMode.UNNECESSARY),
                                l.pickedQty == null ? null : l.pickedQty.setScale(3, RoundingMode.UNNECESSARY)))
                        .toList())).toList();
    }
}
