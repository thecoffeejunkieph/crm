package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.response.WarehouseDashboardSummaryResponse;
import ph.thecoffeejunkie.crm.service.WarehouseDashboardService;

@RestController
@RequestMapping("/api/v1/warehouse-dashboard")
@RequiredArgsConstructor
public class WarehouseDashboardController {

    private final WarehouseDashboardService warehouseDashboardService;

    @GetMapping("/summary")
    public WarehouseDashboardSummaryResponse getSummary() {
        return warehouseDashboardService.getSummary();
    }
}
