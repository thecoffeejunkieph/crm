package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;

import java.math.BigDecimal;
import java.util.List;

/** customerId is required on create and ignored on update; null paymentTerms = the customer's. */
record OrderRequest(Long customerId, PaymentTerms paymentTerms,
                    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal shippingFee,
                    String notes, String terms,
                    @NotNull @Size(max = 200) @Valid List<LineRequest> lines, Long version, String reason) {
}
