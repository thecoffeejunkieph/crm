package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record ReissueRequest(@NotBlank String reason, @NotNull Long version) {
}
