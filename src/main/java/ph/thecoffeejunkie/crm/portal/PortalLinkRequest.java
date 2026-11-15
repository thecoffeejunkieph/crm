package ph.thecoffeejunkie.crm.portal;

import jakarta.validation.constraints.NotBlank;

record PortalLinkRequest(@NotBlank String email) {
}
