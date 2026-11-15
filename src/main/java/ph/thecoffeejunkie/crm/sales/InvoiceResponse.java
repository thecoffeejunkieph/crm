package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** emailedTo is set only on the response that issues the invoice; empty means nobody could be mailed. */
record InvoiceResponse(long id, String number, InvoiceStatus status, int orderRevision, Instant issuedAt,
                       LocalDate dueDate, BigDecimal total, String voidReason, Instant voidedAt, Long replacedById,
                       List<String> emailedTo) {

    InvoiceResponse withEmailedTo(List<String> to) {
        return new InvoiceResponse(id, number, status, orderRevision, issuedAt, dueDate, total, voidReason, voidedAt,
                replacedById, to);
    }
}
