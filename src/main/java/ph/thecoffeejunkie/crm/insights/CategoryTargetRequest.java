package ph.thecoffeejunkie.crm.insights;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** {@code version} is null to create the row, and the row's version to change it. */
record CategoryTargetRequest(@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
                             Long version) {
}
