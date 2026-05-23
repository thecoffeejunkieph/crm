package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotationItemResponse (
        Integer quantity,
        BigDecimal price,
        Integer discount,
        BigDecimal total,
        ProductResponse product
) {}
