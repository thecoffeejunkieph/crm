package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;

/** An order as other modules show it: no lines, no money detail beyond the total. */
public record OrderBrief(long id, String number, long customerId, OrderStatus status, BigDecimal total,
                         Instant confirmedAt) {
}
