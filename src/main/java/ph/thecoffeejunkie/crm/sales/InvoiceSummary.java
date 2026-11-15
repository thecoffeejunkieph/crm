package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvoiceSummary(long id, String number, Instant issuedAt, LocalDate dueDate, BigDecimal total) {
}
