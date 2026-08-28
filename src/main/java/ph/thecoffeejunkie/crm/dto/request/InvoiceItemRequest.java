package ph.thecoffeejunkie.crm.dto.request;

import java.math.BigDecimal;

public record InvoiceItemRequest(
        Long productId,
        Integer quantity,
        BigDecimal price,
        Integer discount,
        BigDecimal total
) {}
