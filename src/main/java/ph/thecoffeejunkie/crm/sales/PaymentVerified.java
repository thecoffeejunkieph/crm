package ph.thecoffeejunkie.crm.sales;

import java.time.Instant;

/** Published in the verifying transaction; repId is the order's rep (null when unassigned, so no target applies). */
public record PaymentVerified(long orderId, long paymentId, Long repId, Instant verifiedAt) {
}
