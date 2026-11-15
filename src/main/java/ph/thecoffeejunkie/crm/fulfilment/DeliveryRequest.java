package ph.thecoffeejunkie.crm.fulfilment;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.util.List;

/** What sales hands over on Release: one line per order line, with display snapshots. */
public record DeliveryRequest(long orderId, String orderNumber, long repUserId, long warehouseId, DeliveryMethod method,
                              String customerName, String shipTo, List<DeliveryLine> lines) {

    public record DeliveryLine(long orderLineId, long productId, String description, ProductUnit unit,
                               BigDecimal qty) {
    }
}
