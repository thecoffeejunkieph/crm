package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    Inventory(StockLedger ledger, Allocator allocator, StockReservationRepository reservations,
              AdjustmentService adjustments) {
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
