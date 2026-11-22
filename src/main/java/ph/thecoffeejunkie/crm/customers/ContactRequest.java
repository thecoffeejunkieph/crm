package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** receivesInvoices null: true for a new contact, unchanged on update. */
public record ContactRequest(@NotBlank String name, @Email @NotBlank String email, String phone,
                             boolean portalEnabled, Long version,
                             Boolean receivesInvoices) {
}
