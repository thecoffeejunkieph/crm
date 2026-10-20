package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;

public record AvailabilityResponse(long warehouseId, String warehouseName, BigDecimal onHand, BigDecimal reserved,
                                   BigDecimal available) {
}
