package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

/** Account-health summary for the customer page: open balance, credit limit (null = none), any overdue order. */
public record CustomerAccount(BigDecimal balance, BigDecimal creditLimit, boolean hasOverdue) {
}
