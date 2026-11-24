package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A null unitCost takes the product's cost; a null warehouseId takes the default warehouse. */
record PoRequest(@NotNull Long supplierId, Long warehouseId, LocalDate expectedOn, String notes,
                        @NotEmpty List<@Valid PoLineRequest> lines) {

    record PoLineRequest(@NotNull Long productId, @NotNull @Positive BigDecimal qty, @PositiveOrZero BigDecimal unitCost) {
    }
}
