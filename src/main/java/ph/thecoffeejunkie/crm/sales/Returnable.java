package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

/** What left a warehouse on this order's line (dispatched) and how much of it is already claimed by refunds. */
public record Returnable(long orderLineId, long warehouseId, BigDecimal shipped, BigDecimal returned) {
}
