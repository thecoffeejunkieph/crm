package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** clientType: WEB (default) or ANDROID. */
record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password,
                    String clientType) {
}
