package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;

record OrderSummary(long id, String number, long customerId, String customerName, OrderStatus status,
                    PaymentStatus paymentStatus, BigDecimal total, BigDecimal balance, String repName,
                    Instant updatedAt) {
}
