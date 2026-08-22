package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockReceiptRequest(
        @NotNull Long productId,
        Long warehouseId,
        @NotNull @Positive Integer quantity,
        String notes
) {}
