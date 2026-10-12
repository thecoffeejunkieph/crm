package ph.thecoffeejunkie.crm.approvals;

import jakarta.validation.constraints.NotNull;

public record DecisionRequest(@NotNull Boolean approve, String note) {
}
