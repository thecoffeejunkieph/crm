package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeadSourceRequest(@NotBlank @Size(max = 60) String name, Long version) {

    public LeadSourceRequest {
        name = name == null ? null : name.trim();
    }
}
