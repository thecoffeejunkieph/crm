package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** version is required on PUT and ignored on POST. */
public record TransferRequest(@NotNull Long fromWarehouseId, @NotNull Long toWarehouseId, Long salesOrderId,
                              String notes, Long version, @NotEmpty List<@Valid TransferLineRequest> lines) {

    public record TransferLineRequest(@NotNull Long productId,
                                      @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty) {
    }
}
