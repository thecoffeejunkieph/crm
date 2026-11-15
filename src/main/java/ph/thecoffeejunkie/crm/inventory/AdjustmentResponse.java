package ph.thecoffeejunkie.crm.inventory;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;

/** No cost or value: those reach only the ADMIN approver, through the approval request. */
public record AdjustmentResponse(long id, String number, AdjustmentStatus status, long warehouseId, long productId,
                                 String sku, String productName, ProductUnit unit, AdjustmentMode mode,
                                 BigDecimal countedQty, BigDecimal delta, String reason, AdjustmentSource source,
                                 String createdByName, Instant createdAt) {
}
