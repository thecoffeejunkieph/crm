package ph.thecoffeejunkie.crm.followups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create and edit body. A SALES caller's {@code assigneeId} is ignored (always themselves); {@code customerId} is
 * ignored on edit, since a follow-up never changes customer; {@code version} is required on edit.
 */
record FollowUpRequest(@NotNull Long customerId, Long orderId, Long assigneeId,
                       @NotBlank @Size(max = 200) String title, @Size(max = 2000) String note,
                       @NotNull LocalDate dueDate, Long version) {
}
