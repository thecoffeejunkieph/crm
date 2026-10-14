package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record RejectRequest(@NotNull Long version, @NotBlank String reason) {
}
