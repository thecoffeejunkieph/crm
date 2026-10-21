package ph.thecoffeejunkie.crm.fulfilment;

import java.time.Instant;

/** Published in the deliver transaction; sales sets the order FULFILLED. */
public record OrderDelivered(long orderId, Instant deliveredAt) {
}
