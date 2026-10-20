package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    Allocator(StockLedger ledger, StockReservationRepository reservations, Notifications notifications) {
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
}
