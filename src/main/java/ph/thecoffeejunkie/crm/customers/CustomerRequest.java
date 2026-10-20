package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CustomerRequest(@NotNull CustomerType type, @NotBlank String displayName, String businessName,
                              String tin, String address, String phone, @Email String email,
                              @PositiveOrZero BigDecimal creditLimit, @NotNull PaymentTerms paymentTerms,
                              String source, String notes, Long version,
                              TaxTreatment taxTreatment, @Size(max = 1000) String socialMedia) {

    // Trim before @Email runs, so padded input is accepted and stored normalised.
    public CustomerRequest {
        email = email == null ? null : email.trim();
        taxTreatment = taxTreatment == null ? TaxTreatment.REGULAR : taxTreatment;
    }
}
