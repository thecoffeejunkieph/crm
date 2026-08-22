package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;
import ph.thecoffeejunkie.crm.dto.response.CountStat;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.dto.response.WarehouseDashboardSummaryResponse;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;
import ph.thecoffeejunkie.crm.repository.WarehouseRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseDashboardService {

    private static final int PENDING_DELIVERIES_PREVIEW_LIMIT = 10;
    private static final List<DeliveryOrderStatus> PENDING_STATUSES =
            List.of(DeliveryOrderStatus.PENDING, DeliveryOrderStatus.READY_FOR_PICKUP, DeliveryOrderStatus.PICKED_UP);

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final WarehouseRepository warehouseRepository;

    public WarehouseDashboardSummaryResponse getSummary() {
        log.info("Building warehouse dashboard summary...");

        long pendingDeliveries = deliveryOrderRepository.countByStatusIn(PENDING_STATUSES);

        LocalDate today = LocalDate.now();
        long deliveredToday = deliveryOrderRepository.countByStatusAndDeliveredAtBetween(
                DeliveryOrderStatus.DELIVERED, today.atStartOfDay(), today.atTime(LocalTime.MAX));

        long totalWarehouses = warehouseRepository.count();

        List<DeliveryOrderResponse> pendingDeliveryPreview = deliveryOrderRepository
                .findByStatusIn(PENDING_STATUSES, PageRequest.of(0, PENDING_DELIVERIES_PREVIEW_LIMIT,
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(CustomMapper::toDeliveryOrderResponse)
                .toList();

        return new WarehouseDashboardSummaryResponse(
                new CountStat(pendingDeliveries, null),
                new CountStat(deliveredToday, null),
                totalWarehouses,
                pendingDeliveryPreview
        );
    }
}
