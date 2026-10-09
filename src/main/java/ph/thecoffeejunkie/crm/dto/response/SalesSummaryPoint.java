package ph.thecoffeejunkie.crm.dto.response;

import java.math.BigDecimal;

/** One bucket of the sales summary chart; {@code period} is yyyy-MM-dd (daily) or yyyy-MM (monthly). */
public record SalesSummaryPoint(
        String period,
        BigDecimal grossSales,
        BigDecimal grossProfit,
        long invoiceCount
) {}
