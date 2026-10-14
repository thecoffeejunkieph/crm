package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record DeclineRequest(@NotNull Long version, @Size(max = 500) String reason) {
}
