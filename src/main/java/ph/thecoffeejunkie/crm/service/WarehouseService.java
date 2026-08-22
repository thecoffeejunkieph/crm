package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.request.WarehouseCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.WarehouseResponse;
import ph.thecoffeejunkie.crm.entity.Warehouse;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.WarehouseRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository repository;

    public WarehouseResponse create(WarehouseCreateRequest request) {
        log.info("Creating warehouse...");

        if (request.defaultWarehouse()) {
            clearExistingDefault();
        }

        WarehouseResponse response = CustomMapper.toWarehouseResponse(repository.save(toWarehouse(request)));

        log.info("Created warehouse {}", response.name());
        return response;
    }

    public PageResponse<WarehouseResponse> findAll(PageRequest pageRequest) {
        log.info("Getting all warehouses...");

        Page<Warehouse> warehousePage = repository.findAll(pageRequest);

        return new PageResponse<>(
                warehousePage.getPageable().getPageNumber() + 1,
                warehousePage.getPageable().getPageSize(),
                warehousePage.getTotalPages(),
                warehousePage.getTotalElements(),
                warehousePage.getContent().stream().map(CustomMapper::toWarehouseResponse).toList()
        );
    }

    public WarehouseResponse findById(Long id) {
        log.info("Getting warehouse with id: {}", id);

        return repository.findById(id)
                .map(CustomMapper::toWarehouseResponse)
                .orElseThrow(() -> {
                    log.warn("Warehouse not found with id: {}", id);
                    return ResourceNotFoundException.of("Warehouse", id);
                });
    }

    public void delete(Long id) {
        log.info("Disabling warehouse with id: {}", id);

        Warehouse warehouse = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Warehouse not found with id: {}", id);
                    return ResourceNotFoundException.of("Warehouse", id);
                });

        warehouse.setActive(false);
        repository.save(warehouse);
    }

    Warehouse resolveDefaultWarehouse() {
        return repository.findByDefaultWarehouseTrue()
                .orElseThrow(() -> new InvalidRequestException("No default warehouse is configured"));
    }

    private void clearExistingDefault() {
        repository.findByDefaultWarehouseTrue().ifPresent(existing -> {
            existing.setDefaultWarehouse(false);
            repository.save(existing);
        });
    }

    private Warehouse toWarehouse(WarehouseCreateRequest request) {
        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.name());
        warehouse.setCode(request.code());
        warehouse.setAddress(request.address());
        warehouse.setDefaultWarehouse(request.defaultWarehouse());

        return warehouse;
    }
}
