package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * cash + withholding must be more than zero (checked by the service, field cashAmount). receivedOn is checked against
 * Manila today by the service: @PastOrPresent would use the JVM zone.
 */
record PaymentRequest(@NotNull PaymentMethod method,
                      @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal cashAmount,
                      @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal withholdingAmount,
                      @NotNull LocalDate receivedOn, @Size(max = 100) String reference) {
}
