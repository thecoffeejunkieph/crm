package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record ReceiveRequest(@NotEmpty List<@Valid ReceiveLineRequest> lines) {

    public record ReceiveLineRequest(@NotNull Long lineId,
                                     @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal receivedQty) {
    }
}
