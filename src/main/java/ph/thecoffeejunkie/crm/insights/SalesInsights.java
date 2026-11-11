package ph.thecoffeejunkie.crm.insights;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A sales rep's month: only orders credited to them ({@code sales_order.rep_id}). No cost or margin.
 * {@code collected} is cash in; {@code netSales} (VAT, shipping and discounts out) drives target and commission.
 */
record SalesInsights(YearMonth month, Target target, BigDecimal collected, BigDecimal netSales,
                     Integer progressPct, BigDecimal remaining,
                     boolean targetHit, BigDecimal commission, BigDecimal invoiced, BigDecimal outstanding,
                     BigDecimal overdue, ToVerify toVerify, List<DayAmount> collectedByDay, NeedsAction needsAction,
                     List<CustomerAmount> topCustomers) implements Serializable {

    record Target(BigDecimal amount, BigDecimal commissionPct) implements Serializable {
    }

    record ToVerify(long count, BigDecimal amount) implements Serializable {
    }

    record DayAmount(LocalDate date, BigDecimal amount) implements Serializable {
    }

    record NeedsAction(List<OrderRef> openQuotes, List<OrderRef> unpaidConfirmed, List<OrderRef> overdueInvoices)
            implements Serializable {
    }

    /** {@code amount}: order total for a quote, otherwise the balance; {@code since}: the date it is sorted on. */
    record OrderRef(long orderId, String number, String customerName, BigDecimal amount, LocalDate since)
            implements Serializable {
    }

    record CustomerAmount(long customerId, String name, BigDecimal collected) implements Serializable {
    }
}
