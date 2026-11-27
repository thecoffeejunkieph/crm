package ph.thecoffeejunkie.crm.fulfilment;

import ph.thecoffeejunkie.crm.shared.ShippingMethod;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.Like;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.SortRequest;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
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
    static final int MAX_PROOF_PHOTOS = 10;
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "id");
    // Keys are the page's column keys. Picked = share of lines fully picked; Dispatched / done = delivered, else dispatched.
    private static final Set<String> SORTABLE = Set.of("number", "status");
    private static final Map<String, SortRequest.SortExpr> SORT_EXPRS = Map.of(
            "customer", SortRequest.alias("customerName", String.class),
            "receiver", SortRequest.alias("receiverName", String.class),
            "created", SortRequest.alias("createdAt", Instant.class),
            "moved", new SortRequest.SortExpr("coalesce(?, ?)", Instant.class, List.of("deliveredAt", "dispatchedAt")),
            "progress", new SortRequest.SortExpr("(select count(*) filter (where picked_qty >= qty)::numeric "
                    + "/ nullif(count(*), 0) from delivery_order_line x where x.delivery_order_id = ?)",
                    BigDecimal.class, List.of("id")));

    private final DeliveryOrderRepository orders;
    private final Inventory inventory;
    private final StorageService storage;
    private final Catalog catalog;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final ApplicationEventPublisher events;
    private final Settings settings;

    DeliveryService(DeliveryOrderRepository orders, Inventory inventory, StorageService storage, Catalog catalog,
                    AuditLog audit, CurrentActor actor, ApplicationEventPublisher events, Settings settings) {
        this.settings = settings;
        this.orders = orders;
        this.inventory = inventory;
        this.storage = storage;
        this.catalog = catalog;
        this.audit = audit;
        this.actor = actor;
        this.events = events;
    }

    @Transactional(readOnly = true)
    PageResponse<DeliveryOrderResponse> list(DeliveryStatus status, Long warehouseId, String q, PageQuery page) {
        Scope me = actor.scope();
        if (warehouseId != null && me.role() == Role.WAREHOUSE && !me.warehouseIds().contains(warehouseId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Warehouse not found");
        }
        Specification<DeliveryOrder> spec = Specification.allOf(
                (r, query, cb) -> status == null ? null : cb.equal(r.get("status"), status),
                (r, query, cb) -> warehouseId == null ? null : cb.equal(r.get("warehouseId"), warehouseId),
                (r, query, cb) -> Like.blank(q) ? null : cb.like(cb.lower(r.get("number")), Like.contains(q), '\\'),
                (r, query, cb) -> switch (me.role()) {
                    case WAREHOUSE -> me.warehouseIds().isEmpty() ? cb.disjunction()
                            : r.get("warehouseId").in(me.warehouseIds());
                    case SALES -> cb.equal(r.get("repUserId"), me.userId());
                    default -> null;
                });
        var sort = SortRequest.<DeliveryOrder>of(page, SORTABLE, Map.of(), SORT_EXPRS, NEWEST);
        Page<DeliveryOrder> p = orders.findAll(spec.and(sort.orderSpec()), sort.pageable());
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

    /** The customer may ask for another courier any time before it is delivered. */
    DeliveryOrderResponse setShippingMethod(long id, ShippingMethod method) {
        DeliveryOrder d = lock(id);
        if (d.status == DeliveryStatus.DELIVERED || d.status == DeliveryStatus.CANCELLED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This delivery order can no longer be changed.");
        }
        if (d.shippingMethod != method) {
            audit.record(AuditAction.UPDATED, ENTITY, d.id, "Changed shipping method on " + d.number,
                    AuditChanges.diff().field("shippingMethod", d.shippingMethod, method));
            d.shippingMethod = method;
            d.updatedAt = Instant.now();
        }
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
        events.publishEvent(new DeliveryDispatched(d.id, d.salesOrderId));
        return toResponse(d);
    }

    /** The photos are stored only after every state check has passed, so a refused delivery stores nothing. */
    DeliveryOrderResponse deliver(long id, List<MultipartFile> photos, String receiverName) {
        DeliveryOrder d = lock(id);
        List<MultipartFile> given = photos == null ? List.of() : photos.stream().filter(p -> !p.isEmpty()).toList();
        if (given.isEmpty() || receiverName == null || receiverName.isBlank()) {
            throw new DomainException(ErrorCode.PROOF_REQUIRED, "A photo and the name of the receiver are required.");
        }
        if (given.size() > MAX_PROOF_PHOTOS) {
            throw invalid("photo", "Attach at most " + MAX_PROOF_PHOTOS + " photos.");
        }
        if (d.method == DeliveryMethod.PICKUP && d.status == DeliveryStatus.PICKING) {
            consume(d);
        } else if (d.method != DeliveryMethod.DELIVERY || d.status != DeliveryStatus.DISPATCHED) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This delivery order cannot be delivered now.");
        }
        // A bad photo throws 400 here and rolls back the pickup's stock postings with it.
        d.proofKeys = new ArrayList<>(given.stream()
                .map(p -> storage.store("delivery-orders/" + d.id + "/proof", p, StorageService.IMAGE_TYPES)).toList());
        d.receiverName = receiverName.trim();
        d.status = DeliveryStatus.DELIVERED;
        d.deliveredAt = Instant.now();
        d.deliveredBy = actor.scope().userId();
        d.updatedAt = d.deliveredAt;
        audit.record(AuditAction.DELIVERED, ENTITY, d.id,
                "Delivered " + d.number + ", received by " + d.receiverName, null);
        events.publishEvent(new OrderDelivered(d.salesOrderId, d.id, d.deliveredAt));
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
        DeliveryOrder d = orders.findByIdForUpdate(id).filter(this::canRead).orElseThrow(DeliveryService::notFound);
        if (!canAct(d)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "Only warehouse staff can do this.");
        }
        return d;
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

    /** Sales acts only on its own orders' delivery orders, and only while an admin allows it. */
    private boolean canAct(DeliveryOrder d) {
        Scope me = actor.scope();
        return switch (me.role()) {
            case ADMIN -> true;
            case WAREHOUSE -> me.warehouseIds().contains(d.warehouseId);
            case SALES -> d.repUserId == me.userId() && settings.bool(SettingKey.FULFILMENT_SALES_CAN_DISPATCH);
            default -> false;
        };
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.NOT_FOUND, "Delivery order not found");
    }

    private static DomainException invalid(String field, String msg) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, msg,
                Map.of("errors", List.of(Map.of("field", field, "message", msg))));
    }

    static List<String> proofUrls(DeliveryOrder d) {
        return d.proofKeys.stream().map(StorageService::url).toList();
    }

    private DeliveryOrderResponse toResponse(DeliveryOrder d) {
        return toResponses(List.of(d)).get(0);
    }

    private List<DeliveryOrderResponse> toResponses(Collection<DeliveryOrder> ds) {
        Map<Long, ProductInfo> products = catalog.info(ds.stream().flatMap(d -> d.lines.stream())
                .map(l -> l.productId).toList());
        return ds.stream().map(d -> new DeliveryOrderResponse(d.id, d.number, d.status, d.method, d.shippingMethod, d.salesOrderId,
                d.orderNumber, d.warehouseId, d.customerName, d.shipTo, d.createdAt, d.dispatchedAt, d.deliveredAt,
                d.receiverName, proofUrls(d), canAct(d), d.lines.stream().map(l ->
                        new DeliveryOrderResponse.DeliveryLineResponse(l.id, l.orderLineId, l.productId,
                                products.get(l.productId).sku(), l.description, l.unit,
                                l.qty.setScale(3, RoundingMode.UNNECESSARY),
                                l.pickedQty == null ? null : l.pickedQty.setScale(3, RoundingMode.UNNECESSARY)))
                        .toList())).toList();
    }
}
