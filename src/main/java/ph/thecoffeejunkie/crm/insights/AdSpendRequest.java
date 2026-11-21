package ph.thecoffeejunkie.crm.insights;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

record AdSpendRequest(@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
