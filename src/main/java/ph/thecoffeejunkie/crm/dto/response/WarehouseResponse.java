package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarehouseResponse(
        Long id,
        String name,
        String code,
        String address,
        boolean active,
        boolean defaultWarehouse
) {}
