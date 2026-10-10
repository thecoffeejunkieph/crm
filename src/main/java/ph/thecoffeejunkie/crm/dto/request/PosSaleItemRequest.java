package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;

// unitPrice overrides the product's list price when set.
public record PosSaleItemRequest(
        Long productId,
        Integer quantity,
        BigDecimal unitPrice,
        Integer discount,
        DiscountType discountType
) {}
