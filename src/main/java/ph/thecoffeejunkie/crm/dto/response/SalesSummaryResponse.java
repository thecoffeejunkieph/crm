package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummaryResponse(
        BigDecimal grossSales,
        BigDecimal grossProfit,
        long invoiceCount,
        List<SalesSummaryPoint> points
) implements Serializable {}
