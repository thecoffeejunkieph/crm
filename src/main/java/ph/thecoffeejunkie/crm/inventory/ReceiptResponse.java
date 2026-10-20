package ph.thecoffeejunkie.crm.inventory;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReceiptResponse(long id, String number, long warehouseId, String supplierName, String reference,
                              LocalDate receivedOn, String notes, String createdByName, Instant createdAt,
                              List<Line> lines) {

    public record Line(long productId, String sku, String name, ProductUnit unit, BigDecimal qty) {
    }
}
