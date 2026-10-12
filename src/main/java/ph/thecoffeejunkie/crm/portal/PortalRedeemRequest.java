package ph.thecoffeejunkie.crm.portal;

import jakarta.validation.constraints.NotBlank;

record PortalRedeemRequest(@NotBlank String token) {
}
