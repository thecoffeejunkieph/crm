package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

import java.math.BigDecimal;

public record ProductSalesResponse(
        Long productId,
        String productName,
        long quantitySold,
        BigDecimal totalSales
) implements Serializable {}
