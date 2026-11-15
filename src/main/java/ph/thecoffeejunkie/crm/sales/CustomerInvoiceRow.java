package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record CustomerInvoiceRow(long id, String number, InvoiceStatus status, long orderId, String orderNumber,
                          Instant issuedAt, LocalDate dueDate, BigDecimal total, PaymentStatus paymentStatus) {
}
