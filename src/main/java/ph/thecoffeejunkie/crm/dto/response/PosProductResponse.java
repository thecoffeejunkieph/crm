package ph.thecoffeejunkie.crm.dto.response;

import ph.thecoffeejunkie.crm.constant.Unit;

import java.math.BigDecimal;

// quantityAvailable is from the default warehouse (0 when the product was never received).
public record PosProductResponse(
        Long id,
        String productName,
        Long categoryId,
        String category,
        Unit unit,
        BigDecimal price,
        String pictureUrl,
        int quantityAvailable
) {}
