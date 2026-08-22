package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryItemResponse(
        Long id,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        int quantityOnHand,
        int quantityReserved,
        int quantityAvailable
) {}
