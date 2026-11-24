package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Receipts add to the balance; payments subtract (their amount is negative). */
record SupplierStatementResponse(BigDecimal opening, List<SupplierStatementEntry> entries, BigDecimal closing) {

    record SupplierStatementEntry(LocalDate date, String kind, String reference, BigDecimal amount, BigDecimal balance) {
    }
}
