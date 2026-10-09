package ph.thecoffeejunkie.crm.dto.response;

import java.io.Serializable;

import java.util.List;

public record DashboardSummaryResponse(
        SalesStat totalSales,
        SalesStat grossProfit,
        CountStat openDeals,
        CountStat newLeads,
        RateStat conversionRate,
        List<MonthlySalesPoint> salesPerformance,
        List<TopSalesRepResponse> topSalesReps,
        List<TopCustomerResponse> topCustomers,
        List<ProductSalesResponse> salesByProduct
) implements Serializable {}
