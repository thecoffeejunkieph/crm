package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.customers.CustomerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BalancesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void overdueBeatsPartial() {
        assertThat(Balances.status(d("100"), d("40"), TODAY.minusDays(1), TODAY)).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(Balances.status(d("100"), d("40"), null, TODAY)).isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    void dueTodayIsNotOverdue() {
        assertThat(Balances.status(d("100"), d("0"), TODAY, TODAY)).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void zeroTotalIsPaid() {
        assertThat(Balances.status(d("0"), d("0"), TODAY.minusDays(9), TODAY)).isEqualTo(PaymentStatus.PAID);
        assertThat(Balances.status(d("100"), d("100"), TODAY.minusDays(9), TODAY)).isEqualTo(PaymentStatus.PAID);
    }

    private static OrderTotals.Result totals(String vatable, String exempt) {
        return new OrderTotals.Result(List.of(), d(vatable), d("0.00"), d(exempt), d("0.00"), d("0.00"));
    }

    @Test
    void withholdingOnePercentBusiness() {
        assertThat(Balances.suggestedWithholding(totals("300.00", "50.00"), d("1"), CustomerType.BUSINESS, d("0"), d("1000")))
                .isEqualTo("3.50");
    }

    @Test
    void withholdingZeroForIndividual() {
        assertThat(Balances.suggestedWithholding(totals("300.00", "50.00"), d("1"), CustomerType.INDIVIDUAL, d("0"), d("1000")))
                .isEqualTo("0.00");
    }

    @Test
    void withholdingNetsEarlierWithholdingAndNeverGoesNegative() {
        assertThat(Balances.suggestedWithholding(totals("300.00", "50.00"), d("1"), CustomerType.BUSINESS, d("2.00"), d("1000")))
                .isEqualTo("1.50");
        assertThat(Balances.suggestedWithholding(totals("300.00", "50.00"), d("1"), CustomerType.BUSINESS, d("5.00"), d("1000")))
                .isEqualTo("0.00");
    }

    @Test
    void withholdingCappedAtBalance() {
        assertThat(Balances.suggestedWithholding(totals("300.00", "50.00"), d("1"), CustomerType.BUSINESS, d("0"), d("0.40")))
                .isEqualTo("0.40");
    }
}
