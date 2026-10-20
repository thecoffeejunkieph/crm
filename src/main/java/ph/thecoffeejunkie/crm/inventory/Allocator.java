package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fills short reservations of one product and warehouse from its available stock, in the caller's transaction. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class Allocator {

    private final StockLedger ledger;
    private final StockReservationRepository reservations;
    private final Notifications notifications;

    private final Catalog catalog;

    Allocator(StockLedger ledger, StockReservationRepository reservations, Notifications notifications,
              Catalog catalog) {
        this.catalog = catalog;
        this.ledger = ledger;
        this.reservations = reservations;
        this.notifications = notifications;
    }

    void allocate(long productId, long warehouseId, Long linkedOrderId) {
        StockLevel level = ledger.lock(productId, warehouseId);
        BigDecimal available = level.available();
        Map<Long, StockReservation> filledOrders = new LinkedHashMap<>();
        for (StockReservation r : reservations.findShort(productId, warehouseId, linkedOrderId)) {
            if (available.signum() <= 0) {
                break;
            }
            BigDecimal fill = r.shortQty.min(available);
            r.shortQty = r.shortQty.subtract(fill);
            r.reservedQty = r.reservedQty.add(fill);
            r.updatedAt = Instant.now();
            ledger.changeReserved(level, fill);
            available = available.subtract(fill);
            filledOrders.putIfAbsent(r.salesOrderId, r);
        }
        // Notifications stores the row in this transaction and pushes it only after commit.
        filledOrders.forEach((orderId, r) -> {
            if (r.repUserId != null && reservations.totalShort(orderId).signum() == 0) {
                notifications.toUser(r.repUserId, NotificationType.ORDER_RESERVED, "ORDER", orderId,
                        "Order " + r.orderNumber + " is fully reserved", null);
            }
        });
    }

    /**
     * A stock count cut on_hand below reserved by excess: moves that much from reservations to short, youngest
     * confirmed first, and tells each affected rep. Lowers reserved on the locked level (before on_hand drops).
     */
    void takeBack(StockLevel level, BigDecimal excess) {
        Map<Long, BigDecimal> perOrder = new LinkedHashMap<>();
        Map<Long, StockReservation> firstOf = new LinkedHashMap<>();
        BigDecimal left = excess;
        for (StockReservation r : reservations.findReservedYoungestFirst(level.productId, level.warehouseId)) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal take = r.reservedQty.min(left);
            r.reservedQty = r.reservedQty.subtract(take);
            r.shortQty = r.shortQty.add(take);
            r.updatedAt = Instant.now();
            left = left.subtract(take);
            perOrder.merge(r.salesOrderId, take, BigDecimal::add);
            firstOf.putIfAbsent(r.salesOrderId, r);
        }
        ledger.changeReserved(level, excess.subtract(left).negate());
        ProductInfo p = catalog.info(java.util.List.of(level.productId)).get(level.productId);
        perOrder.forEach((orderId, qty) -> {
            StockReservation r = firstOf.get(orderId);
            if (r.repUserId != null) {
                notifications.toUser(r.repUserId, NotificationType.RESERVATION_TAKEN, "ORDER", orderId,
                        "Stock count took " + qty.stripTrailingZeros().toPlainString() + " "
                                + p.unit().name().toLowerCase() + " of " + p.sku() + " from order " + r.orderNumber,
                        null);
            }
        });
    }
}
