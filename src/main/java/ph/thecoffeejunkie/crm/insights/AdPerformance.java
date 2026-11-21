package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;
import java.util.List;

/** Ad spend against acquired-customer value, per month and source (spec 2026-11-22). */
record AdPerformance(List<Month> months) {

    /** {@code month} is YYYY-MM; {@code total} sums the rows and recomputes the ratios. */
    record Month(String month, Row total, List<Row> rows) {
    }

    /** The total row has no source. Ratios are null where their divisor is missing or zero. */
    record Row(Long sourceId, String sourceName, BigDecimal spend, long leads, long newCustomers, BigDecimal cohortValue,
               BigDecimal costPerCustomer, BigDecimal valuePerCustomer, BigDecimal roas) {
    }
}
