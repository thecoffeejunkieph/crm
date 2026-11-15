package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The client type comes only from the X-Client-Type header, never from the body. */
record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
}
