package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

/** ADMIN only (also gated by SecurityConfig on /api/suppliers/**). */
@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasRole('ADMIN')")
class SupplierController {

    private final SupplierService suppliers;

    SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    PageResponse<SupplierResponse> list(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "false") boolean includeArchived,
                                        PageQuery page) {
        return suppliers.list(q, includeArchived, page);
    }

    @GetMapping("/{id}")
    SupplierResponse get(@PathVariable long id) {
        return suppliers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SupplierResponse create(@Valid @RequestBody SupplierRequest req) {
        return suppliers.create(req);
    }

    @PutMapping("/{id}")
    SupplierResponse update(@PathVariable long id, @Valid @RequestBody SupplierRequest req) {
        return suppliers.update(id, req);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable long id) {
        suppliers.archive(id);
    }
}
