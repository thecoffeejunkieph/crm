package ph.thecoffeejunkie.crm.warehouses;

import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(@NotBlank String code, @NotBlank String name, String address, boolean isDefault,
                               Long version) {
}
