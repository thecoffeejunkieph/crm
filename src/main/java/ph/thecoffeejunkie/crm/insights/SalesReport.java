package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Money collected over a range, grouped by {@code by} (spec 2026-11-07). */
record SalesReport(LocalDate from, LocalDate to, ReportBy by, Total total, List<ReportRow> rows) {

    record Total(BigDecimal collected, long payments) {
    }
}
