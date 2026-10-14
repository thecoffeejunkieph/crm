package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record PortalOrderSummary(long id, String number, OrderStatus status, PaymentStatus paymentStatus, BigDecimal total,
                          BigDecimal balance, LocalDate quoteValidUntil, Instant quotedAt) {
}
