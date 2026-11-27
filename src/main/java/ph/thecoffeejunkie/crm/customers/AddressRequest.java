package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(@NotBlank @Size(max = 60) String label, @NotBlank @Size(max = 500) String address,
                             boolean defaultAddress, Long version) {
}