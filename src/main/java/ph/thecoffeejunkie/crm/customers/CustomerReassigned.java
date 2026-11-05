package ph.thecoffeejunkie.crm.customers;

/** Published inside the assignment transaction; either rep may be null (unassigned). */
public record CustomerReassigned(long customerId, Long fromRepId, Long toRepId) {
}
