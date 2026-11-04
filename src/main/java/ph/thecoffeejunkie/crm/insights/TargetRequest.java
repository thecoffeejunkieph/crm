package ph.thecoffeejunkie.crm.insights;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** {@code version} is null to create the month's row, and the row's version to change it. */
record TargetRequest(@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
                     @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal commissionPct,
                     Long version) {
}
