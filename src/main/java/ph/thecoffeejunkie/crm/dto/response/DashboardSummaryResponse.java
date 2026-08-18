package ph.thecoffeejunkie.crm.dto.response;

import java.util.List;

public record DashboardSummaryResponse(
        SalesStat totalSales,
        CountStat openDeals,
        CountStat newLeads,
        RateStat conversionRate,
        List<MonthlySalesPoint> salesPerformance,
        List<TopSalesRepResponse> topSalesReps,
        List<TopCustomerResponse> topCustomers
) {}
