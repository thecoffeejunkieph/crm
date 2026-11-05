package ph.thecoffeejunkie.crm.sales;

/** An order became CONFIRMED (staff, portal acceptance or approvals decided). Published inside that transaction. */
public record OrderConfirmed(long orderId, long customerId) {
}
