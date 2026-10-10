package ph.thecoffeejunkie.crm.dto.response;

import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;

/** A quotation/invoice line as shown to the customer - no product cost or internal fields. */
public record PublicLineItem(
        String productName,
        Integer quantity,
        BigDecimal price,
        Integer discount,
        DiscountType discountType,
        BigDecimal total
) {}
