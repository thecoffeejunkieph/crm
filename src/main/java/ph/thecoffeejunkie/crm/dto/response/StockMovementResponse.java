package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.StockMovementType;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockMovementResponse(
        Long id,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        StockMovementType type,
        int quantity,
        int quantityOnHandAfter,
        int quantityReservedAfter,
        String referenceType,
        Long referenceId,
        String notes,
        SalesRepResponse performedBy,
        LocalDateTime createdAt
) {}
