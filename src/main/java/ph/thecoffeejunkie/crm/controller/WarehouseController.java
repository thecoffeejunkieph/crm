package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.WarehouseCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.WarehouseResponse;
import ph.thecoffeejunkie.crm.service.WarehouseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public PageResponse<WarehouseResponse> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return warehouseService.findAll(PageRequest.of(pageNumber - 1, pageSize));
    }

    @GetMapping("/{id}")
    public WarehouseResponse getById(@PathVariable Long id) {
        return warehouseService.findById(id);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping
    public WarehouseResponse create(@RequestBody @Valid WarehouseCreateRequest request) {
        return warehouseService.create(request);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        warehouseService.delete(id);
    }
}
