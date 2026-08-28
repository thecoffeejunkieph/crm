package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ph.thecoffeejunkie.crm.constant.Unit;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank String productName,
        String description,
        Unit unit,
        @NotNull @Positive BigDecimal price
) {}
