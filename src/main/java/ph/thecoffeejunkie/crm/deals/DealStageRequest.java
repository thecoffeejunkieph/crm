package ph.thecoffeejunkie.crm.deals;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DealStageRequest(@NotNull DealStage stage, @Size(max = 500) String lostReason, @NotNull Long version) {

    public DealStageRequest {
        lostReason = lostReason == null || lostReason.isBlank() ? null : lostReason.trim();
    }
}
