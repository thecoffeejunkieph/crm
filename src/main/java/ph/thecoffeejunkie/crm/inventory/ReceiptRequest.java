package ph.thecoffeejunkie.crm.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptRequest(@NotNull Long warehouseId, @NotBlank String supplierName, String reference,
                             @NotNull LocalDate receivedOn, String notes, @NotEmpty List<@Valid ReceiptLineRequest> lines) {

    public record ReceiptLineRequest(@NotNull Long productId, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty) {
    }
}
