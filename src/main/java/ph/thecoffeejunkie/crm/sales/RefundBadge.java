package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

/** How much of the cash the customer paid (verified, withheld tax excluded) has gone back: nothing, some, or all. */
enum RefundBadge {
    NONE, PARTIAL, FULL;

    static RefundBadge of(BigDecimal refunded, BigDecimal cashCollected) {
        if (refunded.signum() <= 0) {
            return NONE;
        }
        return refunded.compareTo(cashCollected) >= 0 ? FULL : PARTIAL;
    }
}
