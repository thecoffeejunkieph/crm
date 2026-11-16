package ph.thecoffeejunkie.crm.sales;

/**
 * A quote stopped awaiting an answer. {@code outcome} is accepted, declined, revised, confirmed or cancelled.
 * Published inside the transaction that did it.
 */
public record QuoteClosed(long orderId, String outcome) {
}
