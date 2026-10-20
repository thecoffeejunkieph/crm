package ph.thecoffeejunkie.crm.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;

public record StockLevelResponse(long productId, String sku, String name, ProductUnit unit, boolean archived,
                                 long warehouseId, BigDecimal onHand, BigDecimal reserved, BigDecimal available,
                                 @JsonProperty("short") BigDecimal short_) {
}
