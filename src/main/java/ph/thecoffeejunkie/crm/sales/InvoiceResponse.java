package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record InvoiceResponse(long id, String number, InvoiceStatus status, int orderRevision, Instant issuedAt,
                       LocalDate dueDate, BigDecimal total, String voidReason, Instant voidedAt, Long replacedById) {
}
