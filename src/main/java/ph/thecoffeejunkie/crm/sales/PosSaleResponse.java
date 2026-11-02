package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

record PosSaleResponse(long orderId, String orderNumber, long invoiceId, String invoiceNumber, BigDecimal total,
                       BigDecimal changeDue) {
}
