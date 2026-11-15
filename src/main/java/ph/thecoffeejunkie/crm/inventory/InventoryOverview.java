package ph.thecoffeejunkie.crm.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Stock health per active product, summed over the warehouses in view. Cost-derived fields ({@code totalValue},
 * {@code withoutCost}, {@code byWarehouse}, a row's {@code unitCost} and {@code value}) are ADMIN-only and omitted otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventoryOverview(BigDecimal totalValue, Long withoutCost, long inStock, long lowStock, long outOfStock,
                                long slowMoving, List<WarehouseValue> byWarehouse, List<ProductStock> products) {

    public enum Health { OK, LOW, OUT }

    public record WarehouseValue(long warehouseId, String name, BigDecimal value) {
    }

    /**
     * {@code daysOfCover}: available ÷ average daily dispatch over 30 days, null when nothing shipped.
     * {@code slow}: stock on hand but nothing dispatched for {@value InventoryOverviewService#SLOW_DAYS} days.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductStock(long productId, String sku, String name, ProductUnit unit, BigDecimal onHand, BigDecimal reserved,
                      BigDecimal available, BigDecimal reorderLevel, Health health, BigDecimal shipped30d,
                      Integer daysOfCover, Instant lastDispatchedAt, boolean slow, BigDecimal unitCost, BigDecimal value) {
    }
}
