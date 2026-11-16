package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

/** How much of what the customer paid has gone back: nothing, some, or all of it. */
enum RefundBadge {
    NONE, PARTIAL, FULL;

    static RefundBadge of(BigDecimal refunded, BigDecimal settled) {
        if (refunded.signum() <= 0) {
            return NONE;
        }
        return refunded.compareTo(settled) >= 0 ? FULL : PARTIAL;
    }
}
