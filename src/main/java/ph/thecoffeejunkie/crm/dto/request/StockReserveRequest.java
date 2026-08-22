package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockReserveRequest(
        @NotNull Long productId,
        Long warehouseId,
        @NotNull @Positive Integer quantity,
        String referenceType,
        Long referenceId
) {}
