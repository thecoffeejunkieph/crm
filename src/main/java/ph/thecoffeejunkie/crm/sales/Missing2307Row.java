package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

record Missing2307Row(long paymentId, long orderId, String orderNumber, String customerName, String repName,
                      LocalDate receivedOn, BigDecimal withholdingAmount) {
}
