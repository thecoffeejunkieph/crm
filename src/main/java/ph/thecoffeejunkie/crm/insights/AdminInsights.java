package ph.thecoffeejunkie.crm.insights;

import ph.thecoffeejunkie.crm.insights.SalesInsights.CustomerAmount;
import ph.thecoffeejunkie.crm.insights.SalesInsights.ToVerify;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** The owner's month, company-wide. The only response with cost-derived figures (margin). */
record AdminInsights(YearMonth month, BigDecimal collected, BigDecimal collectedPrevMonth, BigDecimal invoiced,
                     Margin margin, BigDecimal outstanding, BigDecimal overdue, ToVerify toVerify,
                     List<MonthAmount> collectedByMonth, List<RepRow> reps,
                     List<ProductRow> topProducts, List<CustomerAmount> topCustomers, Operations operations)
        implements Serializable {

    /** {@code pct} is null when no costed line was invoiced in the month. */
    record Margin(BigDecimal amount, BigDecimal pct, long linesWithoutCost) implements Serializable {
    }

    record MonthAmount(YearMonth month, BigDecimal amount) implements Serializable {
    }

    /** {@code repId} null is the "Unassigned" row: orders whose customer had no rep at confirm. */
    record RepRow(Long repId, String name, BigDecimal target, BigDecimal commissionPct, BigDecimal collected,
                  Integer progressPct, boolean targetHit, BigDecimal commission) implements Serializable {
    }

    record ProductRow(long productId, String name, BigDecimal revenue, BigDecimal margin) implements Serializable {
    }

    record Operations(long deliveriesPicking, long deliveriesDispatched, long transfersInTransit,
                      long adjustmentsPendingApproval) implements Serializable {
    }
}
