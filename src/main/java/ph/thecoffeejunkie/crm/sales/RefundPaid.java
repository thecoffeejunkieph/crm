package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;

/** Published in the paying transaction; repId is the order's rep (null when unassigned). */
public record RefundPaid(long orderId, long refundId, Long repId, BigDecimal amount, Instant paidAt) {
}
