package ph.thecoffeejunkie.crm.deals;

/** A deal was deleted. Published inside the transaction that did it. */
public record DealDeleted(long dealId) {
}
