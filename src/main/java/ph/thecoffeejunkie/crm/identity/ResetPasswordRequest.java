package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.NotBlank;

record ResetPasswordRequest(@NotBlank String token, @NotBlank String password) {
}
