package ph.thecoffeejunkie.crm.catalog;

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

@RestController
@RequestMapping("/api/products")
class ProductController {

    private final Catalog catalog;

    ProductController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("@catalogPermissions.canRead()")
    PageResponse<ProductResponse> list(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "false") boolean includeArchived,
                                       PageQuery page) {
        return catalog.list(q, includeArchived, page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@catalogPermissions.canRead()")
    ProductResponse get(@PathVariable long id) {
        return catalog.get(id);
    }

    @PostMapping
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return catalog.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest req) {
        return catalog.update(id, req);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@catalogPermissions.canWrite()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable long id) {
        catalog.archive(id);
    }
}
