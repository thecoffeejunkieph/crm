package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WarehouseCreateRequest(
        @NotBlank String name,
        String code,
        String address,
        boolean defaultWarehouse
) {}
