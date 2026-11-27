package ph.thecoffeejunkie.crm.sales;

/** Published in the rejecting transaction. */
public record PaymentRejected(long orderId, long paymentId) {
}
