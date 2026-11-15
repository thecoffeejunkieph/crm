package ph.thecoffeejunkie.crm.fulfilment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record PickRequest(@NotEmpty List<@Valid PickLineRequest> lines) {

    public record PickLineRequest(@NotNull Long lineId,
                                  @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal pickedQty) {
    }
}
