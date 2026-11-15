package ph.thecoffeejunkie.crm.customers;

import java.time.Instant;

public record CustomerSummary(long id, String displayName, CustomerType type, String phone, String email,
                              String assignedRepName, Instant archivedAt) {
}
