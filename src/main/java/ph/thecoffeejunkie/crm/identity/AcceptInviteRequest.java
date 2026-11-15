package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.NotBlank;

record AcceptInviteRequest(@NotBlank String token, @NotBlank String password) {
}
