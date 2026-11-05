package ph.thecoffeejunkie.crm.leads;

import java.time.Instant;

public record LeadResponse(long id, String contactName, String company, String phone, String email, String socialMedia,
                           String address, String interest, String notes, Long sourceId, String sourceName,
                           Long assignedRepId, String assignedRepName, LeadStatus status, String lostReason,
                           Long customerId, String customerName, Instant convertedAt, Instant createdAt,
                           Instant updatedAt, long version) {
}
