package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Order reservations (spec §3). Every level is locked in (productId, warehouseId) order before it is touched. */
@Service
@Transactional
public class Inventory {

    private static final Comparator<ReserveLine> LINE_ORDER =
            Comparator.comparingLong(ReserveLine::productId).thenComparingLong(ReserveLine::orderLineId);

    private final StockLedger ledger;
    private final Allocator allocator;
    private final StockReservationRepository reservations;

    private final AdjustmentService adjustments;
    private final TransferService transfers;
    private final StockLevelRepository levels;
    private final Notifications notifications;
    private final Catalog catalog;

    Inventory(StockLedger ledger, Allocator allocator, StockReservationRepository reservations,
              AdjustmentService adjustments, TransferService transfers, StockLevelRepository levels,
              Notifications notifications, Catalog catalog) {
        this.notifications = notifications;
        this.catalog = catalog;
        this.transfers = transfers;
        this.levels = levels;
        this.adjustments = adjustments;
        this.ledger = ledger;
        this.allocator = allocator;
        this.reservations = reservations;
    }

    /** Per line, reserves min(qty, available) and records the rest as short. */
    public void reserve(OrderStock o) {
        lockAll(o.lines().stream().map(l -> new Key(l.productId(), o.warehouseId())));
        o.lines().stream().sorted(LINE_ORDER).forEach(l -> {
            StockReservation r = new StockReservation();
            r.orderLineId = l.orderLineId();
            r.productId = l.productId();
            r.reservedQty = BigDecimal.ZERO;
            r.shortQty = BigDecimal.ZERO;
            copyOrder(o, r);
            increase(r, qty(l));
            reservations.save(r);
        });
    }

    /**
     * Applies a revision. A removed line frees all of it; a decrease frees short first, then reserved; an increase
     * reserves what is available and adds the rest to short. Freed stock is re-allocated. Freeing happens before
     * increasing, so stock this order already held stays with it.
     */
    public void adjust(OrderStock o) {
        lockAll(Stream.concat(keysOf(o.orderId()),
                o.lines().stream().map(l -> new Key(l.productId(), o.warehouseId()))));
        // Read after locking: a concurrent allocate may have filled this order's short rows meanwhile.
        List<StockReservation> existing = reservations.findBySalesOrderId(o.orderId());
        if (existing.stream().anyMatch(r -> r.warehouseId != o.warehouseId())) {
            release(o.orderId());
            reserve(o);
            return;
        }
        Map<Long, StockReservation> byLine = existing.stream()
                .collect(Collectors.toMap(r -> r.orderLineId, Function.identity()));
        Map<Long, ReserveLine> wanted = o.lines().stream()
                .collect(Collectors.toMap(ReserveLine::orderLineId, Function.identity()));
        TreeSet<Key> freed = new TreeSet<>();

        for (StockReservation r : existing) {
            ReserveLine l = wanted.get(r.orderLineId);
            if (l == null || l.productId() != r.productId) {
                // Removed, or now a different product: drop it; the loop below re-creates a changed line.
                drop(r, freed);
                byLine.remove(r.orderLineId);
            } else {
                BigDecimal drop = r.reservedQty.add(r.shortQty).subtract(qty(l));
                if (drop.signum() > 0) {
                    decrease(r, drop, freed);
                }
            }
        }
        // Deletes before inserts: a re-created line reuses its order_line_id.
        reservations.flush();
        o.lines().stream().sorted(LINE_ORDER).forEach(l -> {
            StockReservation r = byLine.get(l.orderLineId());
            if (r == null) {
                r = new StockReservation();
                r.orderLineId = l.orderLineId();
                r.productId = l.productId();
                r.reservedQty = BigDecimal.ZERO;
                r.shortQty = BigDecimal.ZERO;
            }
            copyOrder(o, r);
            BigDecimal add = qty(l).subtract(r.reservedQty.add(r.shortQty));
            if (add.signum() > 0) {
                increase(r, add);
            }
            reservations.save(r);
        });
        freed.forEach(k -> allocator.allocate(k.productId(), k.warehouseId(), null));
    }

    /** Deletes the order's reservations, lowers reserved and re-allocates what was freed. */
    public void release(long orderId) {
        lockAll(keysOf(orderId));
        List<StockReservation> existing = reservations.findBySalesOrderId(orderId);
        TreeSet<Key> freed = new TreeSet<>();
        for (StockReservation r : existing) {
            drop(r, freed);
        }
        // Flush the deletes now: a following reserve() re-inserts the same order_line_id.
        reservations.flush();
        freed.forEach(k -> allocator.allocate(k.productId(), k.warehouseId(), null));
    }

    /**
     * A POSTED adjustment made by the system (short pick, transit loss), no approval. For a decrease the caller
     * must already have lowered reserved by anything it held, so nothing is taken from other orders.
     * TRANSIT_LOSS records without moving stock. Returns the adjustment id.
     */
    public long postSystemAdjustment(long warehouseId, long productId, BigDecimal delta, AdjustmentSource source,
                                     String reason, String refType, long refId) {
        return adjustments.postSystem(warehouseId, productId, delta, source, reason, refType, refId);
    }

    /** Movements and adjustments made for a delivery order carry this ref_type. */
    private static final String DELIVERY_ORDER = "DELIVERY_ORDER";

    /**
     * Dispatch: per reservation lowers reserved, then posts DISPATCH of -qty, then deletes the reservations. Any
     * short line refuses the whole order with ORDER_SHORT.
     */
    public void consume(long orderId, String refNumber, long refId) {
        lockAll(keysOf(orderId));
        List<StockReservation> rs = reservations.findBySalesOrderId(orderId);
        List<Map<String, Object>> shortLines = rs.stream().filter(r -> r.shortQty.signum() > 0)
                .<Map<String, Object>>map(r -> Map.of("orderLineId", r.orderLineId, "shortQty", r.shortQty)).toList();
        if (!shortLines.isEmpty()) {
            throw new DomainException(ErrorCode.ORDER_SHORT, "Some lines are short of stock.",
                    Map.of("lines", shortLines));
        }
        rs.stream().sorted(Comparator.comparingLong((StockReservation r) -> r.productId)
                .thenComparingLong(r -> r.orderLineId)).forEach(r -> {
            if (r.reservedQty.signum() > 0) {
                ledger.changeReserved(ledger.lock(r.productId, r.warehouseId), r.reservedQty.negate());
                ledger.post(MovementType.DISPATCH, r.productId, r.warehouseId, r.reservedQty.negate(),
                        DELIVERY_ORDER, refId, refNumber, null);
            }
            reservations.delete(r);
        });
    }

    /**
     * The picker found gap less than the line's reservation. Under the level lock: moves gap from reserved to short,
     * lowers reserved, then posts the SHORT_PICK adjustment, so it never takes stock from other orders. Tells the rep.
     */
    public void shortPick(long orderLineId, BigDecimal gap, String doNumber, long doId) {
        List<Object[]> key = reservations.lineKey(orderLineId);
        if (key.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_STATE, "This line has no reservation.");
        }
        StockLevel level = ledger.lock((Long) key.get(0)[0], (Long) key.get(0)[1]);
        StockReservation r = reservations.findByOrderLineId(orderLineId).orElseThrow();
        if (gap.compareTo(r.reservedQty) > 0) {
            throw new DomainException(ErrorCode.INVALID_STATE, "Only " + r.reservedQty.stripTrailingZeros()
                    .toPlainString() + " is reserved for this line. Receive stock before picking it again.");
        }
        r.reservedQty = r.reservedQty.subtract(gap);
        r.shortQty = r.shortQty.add(gap);
        r.updatedAt = Instant.now();
        ledger.changeReserved(level, gap.negate());
        String reason = "Short pick on " + doNumber;
        adjustments.postSystem(r.warehouseId, r.productId, gap.negate(), AdjustmentSource.SHORT_PICK, reason,
                DELIVERY_ORDER, doId);
        if (r.repUserId != null) {
            ProductInfo p = catalog.info(List.of(r.productId)).get(r.productId);
            notifications.toUser(r.repUserId, NotificationType.SHORT_PICK, "ORDER", r.salesOrderId,
                    "Order " + r.orderNumber + " was short picked",
                    gap.stripTrailingZeros().toPlainString() + " " + p.unit() + " of " + p.name() + " short on "
                            + doNumber + ".");
        }
    }

    /**
     * One linked DRAFT transfer per source warehouse that can cover part of the order's shortage, sent to the
     * order's warehouse. No shortage, or no stock elsewhere, gives an empty list. The caller owns the permission check.
     */
    public List<TransferResponse> coverShortage(long orderId, long actorId) {
        List<StockReservation> rs = reservations.findBySalesOrderId(orderId);
        if (rs.isEmpty()) {
            return List.of();
        }
        long shipFrom = rs.get(0).warehouseId;
        Map<Long, BigDecimal> shortByProduct = rs.stream().filter(r -> r.shortQty.signum() > 0)
                .collect(Collectors.toMap(r -> r.productId, r -> r.shortQty, BigDecimal::add));
        if (shortByProduct.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, BigDecimal>> available = new HashMap<>();
        for (StockLevel l : levels.findByProductIdIn(shortByProduct.keySet())) {
            available.computeIfAbsent(l.productId, k -> new HashMap<>()).put(l.warehouseId, l.available());
        }
        return CoverShortage.plan(shortByProduct, available, shipFrom).entrySet().stream()
                .map(e -> transfers.createLinked(e.getKey(), shipFrom, orderId, e.getValue(), actorId)).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, LineStock> stockFor(long orderId) {
        Map<Long, LineStock> out = new HashMap<>();
        for (StockReservation r : reservations.findBySalesOrderId(orderId)) {
            out.put(r.orderLineId, new LineStock(r.orderLineId, r.reservedQty, r.shortQty));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public BigDecimal totalShort(long orderId) {
        return reservations.totalShort(orderId).setScale(3, RoundingMode.UNNECESSARY);
    }

    private void increase(StockReservation r, BigDecimal qty) {
        StockLevel level = ledger.lock(r.productId, r.warehouseId);
        BigDecimal take = qty.min(level.available().max(BigDecimal.ZERO));
        r.reservedQty = r.reservedQty.add(take);
        r.shortQty = r.shortQty.add(qty.subtract(take));
        r.updatedAt = Instant.now();
        ledger.changeReserved(level, take);
    }

    /** Deletes r and lowers reserved by what it held. */
    private void drop(StockReservation r, TreeSet<Key> freed) {
        if (r.reservedQty.signum() > 0) {
            ledger.changeReserved(ledger.lock(r.productId, r.warehouseId), r.reservedQty.negate());
            freed.add(new Key(r.productId, r.warehouseId));
        }
        reservations.delete(r);
    }

    /** Frees qty (less than r's total) from short first, then from reserved. */
    private void decrease(StockReservation r, BigDecimal qty, TreeSet<Key> freed) {
        StockLevel level = ledger.lock(r.productId, r.warehouseId);
        BigDecimal fromShort = qty.min(r.shortQty);
        BigDecimal fromReserved = qty.subtract(fromShort);
        r.shortQty = r.shortQty.subtract(fromShort);
        r.reservedQty = r.reservedQty.subtract(fromReserved);
        r.updatedAt = Instant.now();
        if (fromReserved.signum() > 0) {
            ledger.changeReserved(level, fromReserved.negate());
            freed.add(new Key(r.productId, r.warehouseId));
        }
    }

    /** Only this order's own adjust moves its reservations between warehouses, so the keys cannot change. */
    private Stream<Key> keysOf(long orderId) {
        return reservations.levelKeys(orderId).stream().map(k -> new Key((Long) k[0], (Long) k[1]));
    }

    private void lockAll(Stream<Key> keys) {
        keys.collect(Collectors.toCollection(TreeSet::new)).forEach(k -> ledger.lock(k.productId(), k.warehouseId()));
    }

    private static void copyOrder(OrderStock o, StockReservation r) {
        r.salesOrderId = o.orderId();
        r.orderNumber = o.orderNumber();
        r.repUserId = o.repUserId();
        r.warehouseId = o.warehouseId();
        r.confirmedAt = o.confirmedAt();
    }

    private static BigDecimal qty(ReserveLine l) {
        return l.qty().setScale(3, RoundingMode.UNNECESSARY);
    }

    private record Key(long productId, long warehouseId) implements Comparable<Key> {
        @Override
        public int compareTo(Key k) {
            int c = Long.compare(productId, k.productId);
            return c != 0 ? c : Long.compare(warehouseId, k.warehouseId);
        }
    }
}
