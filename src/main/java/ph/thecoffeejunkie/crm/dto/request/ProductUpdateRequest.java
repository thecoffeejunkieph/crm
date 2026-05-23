package ph.thecoffeejunkie.crm.dto.request;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String productName,
        String description,
        BigDecimal price
) {}