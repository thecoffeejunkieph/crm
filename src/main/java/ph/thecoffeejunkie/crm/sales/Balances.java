package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Derived payment status and the withholding suggestion (spec §3, §4). */
final class Balances {

    private Balances() {
    }

    static PaymentStatus status(BigDecimal total, BigDecimal settled, LocalDate dueDate, LocalDate today) {
        BigDecimal balance = total.subtract(settled);
        if (balance.signum() <= 0) {
            return PaymentStatus.PAID;
        }
        if (dueDate != null && today.isAfter(dueDate)) {
            return PaymentStatus.OVERDUE;
        }
        return settled.signum() == 0 ? PaymentStatus.UNPAID : PaymentStatus.PARTIAL;
    }

    /** Withholding is on the amount net of VAT (after any whole-order discount, which the totals already reflect). */
    static BigDecimal withholding(OrderTotals.Result t, BigDecimal percent) {
        BigDecimal base = t.vatableSales().add(t.vatExemptSales()).add(t.zeroRatedSales());
        return base.multiply(percent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    /** What is left to withhold: the order's share less what earlier payments withheld, never more than the balance. */
    static BigDecimal suggestedWithholding(OrderTotals.Result t, BigDecimal percent, BigDecimal withheld, BigDecimal balance) {
        return withholding(t, percent).subtract(withheld).min(balance).max(new BigDecimal("0.00")).setScale(2, RoundingMode.HALF_UP);
    }
}
