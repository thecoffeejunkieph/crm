package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

/** Open balance split by days past due: not yet due, 1-30, 31-60, 61-90, over 90. */
record AgingBuckets(BigDecimal current, BigDecimal days1to30, BigDecimal days31to60, BigDecimal days61to90,
                    BigDecimal over90, BigDecimal total) {

    BigDecimal overdue() {
        return total.subtract(current);
    }
}