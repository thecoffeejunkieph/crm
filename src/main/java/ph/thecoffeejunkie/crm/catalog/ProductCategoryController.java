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

import java.util.List;

@RestController
@RequestMapping("/api/product-categories")
class ProductCategoryController {

    private final ProductCategories categories;
    private final CatalogPermissions permissions;

    ProductCategoryController(ProductCategories categories, CatalogPermissions permissions) {
        this.categories = categories;
        this.permissions = permissions;
    }

    @GetMapping
    @PreAuthorize("@catalogPermissions.canRead()")
    List<ProductCategoryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.list(includeArchived && permissions.canWrite());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductCategoryResponse add(@Valid @RequestBody ProductCategoryRequest req) {
        return categories.add(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductCategoryResponse rename(@PathVariable long id, @Valid @RequestBody ProductCategoryRequest req) {
        return categories.rename(id, req);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductCategoryResponse archive(@PathVariable long id) {
        return categories.setArchived(id, true);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@catalogPermissions.canWrite()")
    ProductCategoryResponse restore(@PathVariable long id) {
        return categories.setArchived(id, false);
    }
}
