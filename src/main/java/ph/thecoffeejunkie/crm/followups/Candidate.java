package ph.thecoffeejunkie.crm.followups;

/** What a rule found: one follow-up to create. {@code key} is the idempotency key (unique across all auto follow-ups). */
public record Candidate(long customerId, Long orderId, Long repHint, String key, String title, String note) {
}
