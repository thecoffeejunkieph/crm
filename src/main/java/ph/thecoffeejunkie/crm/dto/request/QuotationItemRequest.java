package ph.thecoffeejunkie.crm.dto.request;

import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;

public record QuotationItemRequest (
   Long productId,
   Integer quantity,
   BigDecimal price,
   Integer discount,
   DiscountType discountType,
   BigDecimal total
) {}
