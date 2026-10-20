package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** COUNTED needs countedQty; DELTA needs a non-zero delta (checked by the service). */
public record AdjustmentRequest(@NotNull Long warehouseId, @NotNull Long productId, @NotNull AdjustmentMode mode,
                                @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal countedQty,
                                @Digits(integer = 9, fraction = 3) BigDecimal delta, @NotBlank String reason) {
}
