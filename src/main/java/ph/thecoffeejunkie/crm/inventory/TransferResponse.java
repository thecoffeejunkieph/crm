package ph.thecoffeejunkie.crm.inventory;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransferResponse(long id, String number, TransferStatus status, long fromWarehouseId,
                               long toWarehouseId, Long salesOrderId, String notes, long version,
                               String createdByName, Instant createdAt, Instant dispatchedAt, Instant receivedAt,
                               List<TransferLineResponse> lines) {

    public record TransferLineResponse(long id, long productId, String sku, String name, ProductUnit unit,
                                       BigDecimal qty, BigDecimal receivedQty) {
    }
}
