package ph.thecoffeejunkie.crm.deals;

import jakarta.validation.constraints.NotNull;

public record LinkRequest(@NotNull Long orderId) {
}
