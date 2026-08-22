package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.StockReceiptRequest;
import ph.thecoffeejunkie.crm.dto.request.StockReleaseRequest;
import ph.thecoffeejunkie.crm.dto.request.StockReserveRequest;
import ph.thecoffeejunkie.crm.dto.response.InventoryItemResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.StockMovementResponse;
import ph.thecoffeejunkie.crm.service.InventoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public PageResponse<InventoryItemResponse> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) Long productId,
                                                        @RequestParam(required = false) Long warehouseId) {
        return inventoryService.listInventory(productId, warehouseId, PageRequest.of(pageNumber - 1, pageSize));
    }

    @GetMapping("/{productId}/movements")
    public PageResponse<StockMovementResponse> getMovements(@PathVariable Long productId,
                                                              @RequestParam(defaultValue = "0") int pageNumber,
                                                              @RequestParam(defaultValue = "10") int pageSize) {
        return inventoryService.getMovementHistory(productId, PageRequest.of(pageNumber - 1, pageSize));
    }

    @GetMapping("/movements")
    public PageResponse<StockMovementResponse> getAllMovements(@RequestParam(defaultValue = "0") int pageNumber,
                                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                                 @RequestParam(required = false) Long warehouseId) {
        return inventoryService.getRecentMovements(warehouseId, PageRequest.of(pageNumber - 1, pageSize));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping("/receive")
    public InventoryItemResponse receive(@RequestBody @Valid StockReceiptRequest request) {
        return inventoryService.receive(request);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping("/reserve")
    public InventoryItemResponse reserve(@RequestBody @Valid StockReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping("/release")
    public InventoryItemResponse release(@RequestBody @Valid StockReleaseRequest request) {
        return inventoryService.release(request);
    }
}
