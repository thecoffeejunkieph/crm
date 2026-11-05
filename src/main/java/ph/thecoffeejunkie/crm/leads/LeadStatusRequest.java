package ph.thecoffeejunkie.crm.leads;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeadStatusRequest(@NotNull LeadStatus status, @Size(max = 500) String lostReason, @NotNull Long version) {

    public LeadStatusRequest {
        lostReason = lostReason == null || lostReason.isBlank() ? null : lostReason.trim();
    }
}
