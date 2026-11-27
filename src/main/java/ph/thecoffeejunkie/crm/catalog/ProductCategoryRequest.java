package ph.thecoffeejunkie.crm.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ProductCategoryRequest(@NotBlank @Size(max = 100) String name, Long version) {

    ProductCategoryRequest {
        name = name == null ? null : name.trim();
    }
}
