package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;

/**
 * One row of any sales report; fields a report does not use are null. {@code id} is the rep, customer or product,
 * and null for the "Unassigned" and "Shipping" rows.
 */
record ReportRow(String month, Long id, String name, BigDecimal collected, Long payments, BigDecimal sharePct,
                 BigDecimal margin, BigDecimal marginPct, Integer linesWithoutCost) {
}
