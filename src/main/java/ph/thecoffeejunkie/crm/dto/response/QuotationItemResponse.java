package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotationItemResponse (
        Integer quantity,
        BigDecimal price,
        Integer discount,
        DiscountType discountType,
        BigDecimal total,
        ProductResponse product
) {}
