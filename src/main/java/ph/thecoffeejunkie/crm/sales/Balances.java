package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.customers.CustomerType;

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

    /** What is left to withhold: the order's share less what earlier payments withheld, never more than the balance. */
    static BigDecimal suggestedWithholding(OrderTotals.Result t, BigDecimal percent, CustomerType type,
                                           BigDecimal withheld, BigDecimal balance) {
        BigDecimal zero = new BigDecimal("0.00");
        if (type != CustomerType.BUSINESS) {
            return zero;
        }
        BigDecimal base = t.vatableSales().add(t.vatExemptSales()).add(t.zeroRatedSales());
        BigDecimal left = base.multiply(percent).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP).subtract(withheld);
        return left.min(balance).max(zero).setScale(2, RoundingMode.HALF_UP);
    }
}
