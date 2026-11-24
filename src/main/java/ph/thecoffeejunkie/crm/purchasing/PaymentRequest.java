package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

record PaymentRequest(@NotNull @Positive BigDecimal amount, @NotNull LocalDate paidOn, String method, String reference,
                      String notes) {
}
