package ph.thecoffeejunkie.crm.leads;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import ph.thecoffeejunkie.crm.customers.CustomerRequest;

/** Exactly one of a new customer or an existing customer's id. */
public record LeadConvertRequest(@Valid CustomerRequest customer, Long customerId, @NotNull Long version) {

    @JsonIgnore
    @AssertTrue(message = "Give either a new customer or an existing customer, not both.")
    boolean isOneTarget() {
        return (customer == null) != (customerId == null);
    }
}
