package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

record SupplierPaymentRequest(@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount, @NotNull LocalDate paidOn, @Size(max = 50) String method, @Size(max = 100) String reference,
                              @Size(max = 1000) String notes, Long purchaseOrderId) {
}
