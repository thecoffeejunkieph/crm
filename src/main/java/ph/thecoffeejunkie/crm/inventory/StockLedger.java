package ph.thecoffeejunkie.crm.inventory;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The only writer of stock_level. Runs in the caller's transaction. Callers touching several levels lock them in
 * (productId, warehouseId) order. Changes to a locked level reach the database as one UPDATE at flush, so the
 * CHECK (on_hand >= reserved) only sees the final values; this class checks the same rule after every change.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class StockLedger {

    private final StockLevelRepository levels;
    private final StockMovementRepository movements;
    private final Allocator allocator;
    private final CurrentActor actor;

    StockLedger(StockLevelRepository levels, StockMovementRepository movements, @Lazy Allocator allocator,
                CurrentActor actor) {
        this.levels = levels;
        this.movements = movements;
        this.allocator = allocator;
        this.actor = actor;
    }

    /** SELECT … FOR UPDATE, creating the row with zeros first when missing. Re-locking in the same tx is free. */
    StockLevel lock(long productId, long warehouseId) {
        levels.insertIfMissing(productId, warehouseId);
        return levels.findByProductIdAndWarehouseId(productId, warehouseId).orElseThrow();
    }

    void post(MovementType type, long productId, long warehouseId, BigDecimal qty, String refType, long refId,
              String reason, Long linkedOrderId) {
        StockLevel level = lock(productId, warehouseId);
        if (type == MovementType.ADJUSTMENT && qty.signum() < 0) {
            // A count may cut on_hand below reserved: take the excess from the youngest orders first.
            BigDecimal excess = level.reserved.subtract(level.onHand.add(qty));
            if (excess.signum() > 0) {
                allocator.takeBack(level, excess);
            }
        }
        StockMovement m = new StockMovement();
        m.productId = productId;
        m.warehouseId = warehouseId;
        m.type = type;
        m.qty = qty;
        m.refType = refType;
        m.refId = refId;
        m.reason = reason;
        m.createdBy = actor.staff().map(StaffPrincipal::userId).orElse(null);
        movements.save(m);
        level.onHand = level.onHand.add(qty);
        touched(level);
        if (qty.signum() > 0) {
            allocator.allocate(productId, warehouseId, linkedOrderId);
        }
    }

    /**
     * Changes reserved on a level the caller already locked. To lower on_hand and reserved together (dispatch,
     * short pick), call this with the negative amount first, then {@link #post} the negative movement.
     */
    void changeReserved(StockLevel level, BigDecimal delta) {
        level.reserved = level.reserved.add(delta);
        if (level.reserved.signum() < 0) {
            throw new IllegalStateException("Reserved below zero for product " + level.productId);
        }
        touched(level);
    }

    private static void touched(StockLevel level) {
        if (level.onHand.signum() < 0 || level.onHand.compareTo(level.reserved) < 0) {
            throw new DomainException(ErrorCode.INSUFFICIENT_STOCK, "Not enough stock for this change.");
        }
        level.updatedAt = Instant.now();
    }
}
