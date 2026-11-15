package ph.thecoffeejunkie.crm.fulfilment;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DeliveryOrderResponse(long id, String number, DeliveryStatus status, DeliveryMethod method,
                                    long salesOrderId, String orderNumber, long warehouseId, String customerName,
                                    String shipTo, Instant createdAt, Instant dispatchedAt, Instant deliveredAt,
                                    String receiverName, List<String> proofUrls, List<DeliveryLineResponse> lines) {

    public record DeliveryLineResponse(long id, long orderLineId, long productId, String sku, String description,
                                       ProductUnit unit, BigDecimal qty, BigDecimal pickedQty) {
    }
}
