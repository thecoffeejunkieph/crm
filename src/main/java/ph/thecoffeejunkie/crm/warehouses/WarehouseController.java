package ph.thecoffeejunkie.crm.warehouses;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warehouses")
class WarehouseController {

    private final Warehouses warehouses;

    WarehouseController(Warehouses warehouses) {
        this.warehouses = warehouses;
    }

    @GetMapping
    List<WarehouseResponse> list() {
        return warehouses.listActive();
    }

    @GetMapping("/{id}")
    WarehouseResponse get(@PathVariable long id) {
        return warehouses.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    WarehouseResponse create(@Valid @RequestBody WarehouseRequest req) {
        return warehouses.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    WarehouseResponse update(@PathVariable long id, @Valid @RequestBody WarehouseRequest req) {
        if (req.version() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "version is required",
                    Map.of("errors", List.of(Map.of("field", "version", "message", "must not be null"))));
        }
        return warehouses.update(id, req, req.version());
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    void archive(@PathVariable long id) {
        warehouses.archive(id);
    }
}
