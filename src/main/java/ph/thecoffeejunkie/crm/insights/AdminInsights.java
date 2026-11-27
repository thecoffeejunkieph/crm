package ph.thecoffeejunkie.crm.insights;

import ph.thecoffeejunkie.crm.insights.SalesInsights.CustomerAmount;
import ph.thecoffeejunkie.crm.insights.SalesInsights.DayAmount;
import ph.thecoffeejunkie.crm.insights.SalesInsights.ToVerify;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** The owner's week, month or year ({@code from}..{@code to} inclusive), company-wide; {@code collectedPrev} is the period before. The only response with cost-derived figures (margin). */
record AdminInsights(YearMonth month, String period, LocalDate from, LocalDate to, BigDecimal collected,
                     BigDecimal collectedPrev, BigDecimal invoiced,
                     Margin margin, BigDecimal outstanding, BigDecimal overdue, ToVerify toVerify,
                     List<DayAmount> collectedTrend, List<RepRow> reps,
                     List<ProductRow> topProducts, List<CustomerAmount> topCustomers, Operations operations)
        implements Serializable {

    /** {@code pct} is null when no costed line was invoiced in the month. */
    record Margin(BigDecimal amount, BigDecimal pct, long linesWithoutCost) implements Serializable {
    }

    /** {@code repId} null is the "Unassigned" row: orders whose customer had no rep at confirm. */
    record RepRow(Long repId, String name, BigDecimal target, BigDecimal commissionPct, BigDecimal collected,
                  BigDecimal netSales, Integer progressPct, boolean targetHit, BigDecimal commission) implements Serializable {
    }

    record ProductRow(long productId, String name, BigDecimal revenue, BigDecimal margin) implements Serializable {
    }

    record Operations(long deliveriesPicking, long deliveriesDispatched, long transfersInTransit,
                      long adjustmentsPendingApproval) implements Serializable {
    }
}
