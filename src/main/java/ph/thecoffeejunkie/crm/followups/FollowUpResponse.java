package ph.thecoffeejunkie.crm.followups;

import java.time.Instant;
import java.time.LocalDate;

record FollowUpResponse(long id, long customerId, String customerName, Long orderId, String orderNumber,
                        long assigneeId, String assigneeName, String title, String note, LocalDate dueDate,
                        Instant doneAt, long version) {
}
