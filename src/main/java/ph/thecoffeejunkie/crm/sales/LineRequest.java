package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * id names an existing line of this order to update in place; null (or an id no order has) adds a line. Null
 * unitPrice = catalog default; null discountPct = 0; blank description = product name.
 */
record LineRequest(Long id, @NotNull Long productId,
                   @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty,
                   @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
                   @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPct,
                   @Size(max = 500) String description) {
}
