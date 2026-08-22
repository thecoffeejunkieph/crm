package ph.thecoffeejunkie.crm.dto.response;

import java.util.List;

public record WarehouseDashboardSummaryResponse(
        CountStat pendingDeliveries,
        CountStat deliveredToday,
        long totalWarehouses,
        List<DeliveryOrderResponse> pendingDeliveryPreview
) {}
