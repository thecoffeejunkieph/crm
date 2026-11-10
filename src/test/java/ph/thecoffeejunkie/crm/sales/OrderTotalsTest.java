package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.catalog.TaxClass;
import ph.thecoffeejunkie.crm.customers.TaxTreatment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTotalsTest {

    private static OrderTotals.Line line(String qty, String price, String pct, TaxClass c) {
        return new OrderTotals.Line(new BigDecimal(qty), new BigDecimal(price), new BigDecimal(pct), c);
    }

    private static final List<OrderTotals.Line> MIXED = List.of(line("1", "112.00", "0", TaxClass.VATABLE),
            line("1", "50.00", "0", TaxClass.VAT_EXEMPT), line("1", "30.00", "0", TaxClass.ZERO_RATED));

    @Test
    void wholeOrderDiscountComesOffGoodsBeforeVatAndNotShipping() {
        // Goods 1,120 VATable + 280 exempt = 1,400; 10% is 140, spread 112 / 28. Shipping 56 is untouched.
        var lines = List.of(line("1", "1120.00", "0", TaxClass.VATABLE), line("1", "280.00", "0", TaxClass.VAT_EXEMPT));
        var discount = OrderTotals.discountAmount(DiscountType.PERCENT, new BigDecimal("10"), new BigDecimal("1400.00"));
        var r = OrderTotals.compute(lines, new BigDecimal("56"), TaxTreatment.REGULAR, discount);
        assertThat(discount).isEqualByComparingTo("140.00");
        assertThat(r.vatableSales()).isEqualByComparingTo("950.00"); // (1120 - 112 + 56) / 1.12
        assertThat(r.vat()).isEqualByComparingTo("114.00");
        assertThat(r.vatExemptSales()).isEqualByComparingTo("252.00");
        assertThat(r.total()).isEqualByComparingTo("1316.00");
        assertThat(r.lineTotals()).containsExactly(new BigDecimal("1120.00"), new BigDecimal("280.00"));
    }

    @Test
    void fixedDiscountPartsAlwaysSumToTheDiscount() {
        var lines = List.of(line("1", "33.33", "0", TaxClass.VATABLE), line("1", "33.33", "0", TaxClass.VAT_EXEMPT),
                line("1", "33.34", "0", TaxClass.ZERO_RATED));
        var r = OrderTotals.compute(lines, BigDecimal.ZERO, TaxTreatment.REGULAR, new BigDecimal("10.01"));
        assertThat(r.total()).isEqualByComparingTo("89.99");
    }

    @Test
    void singleVatableLine() {
        var r = OrderTotals.compute(List.of(line("3", "112.00", "0", TaxClass.VATABLE)), BigDecimal.ZERO,
                TaxTreatment.REGULAR);
        assertThat(r.vatableSales()).isEqualTo("300.00");
        assertThat(r.vat()).isEqualTo("36.00");
        assertThat(r.total()).isEqualTo("336.00");
        assertThat(r.lineTotals()).containsExactly(new BigDecimal("336.00"));
    }

    @Test
    void remainderVat() {
        var r = OrderTotals.compute(List.of(line("1", "100.00", "0", TaxClass.VATABLE)), BigDecimal.ZERO,
                TaxTreatment.REGULAR);
        assertThat(r.vatableSales()).isEqualTo("89.29");
        assertThat(r.vat()).isEqualTo("10.71");
        assertThat(r.total()).isEqualTo("100.00");
    }

    @Test
    void discountRoundsPerLine() {
        var r = OrderTotals.compute(List.of(line("2.5", "99.99", "10", TaxClass.VATABLE)), BigDecimal.ZERO,
                TaxTreatment.REGULAR);
        assertThat(r.lineTotals()).containsExactly(new BigDecimal("224.98"));
    }

    @Test
    void mixedClassesWithShipping() {
        var r = OrderTotals.compute(MIXED, new BigDecimal("56"), TaxTreatment.REGULAR);
        assertThat(r.vatableSales()).isEqualTo("150.00");
        assertThat(r.vat()).isEqualTo("18.00");
        assertThat(r.vatExemptSales()).isEqualTo("50.00");
        assertThat(r.zeroRatedSales()).isEqualTo("30.00");
        assertThat(r.total()).isEqualTo("248.00");
    }

    @Test
    void zeroRatedCustomerOverridesAll() {
        var r = OrderTotals.compute(MIXED, new BigDecimal("56"), TaxTreatment.ZERO_RATED);
        assertThat(r.zeroRatedSales()).isEqualTo("248.00");
        assertThat(r.vatableSales()).isEqualTo("0.00");
        assertThat(r.vat()).isEqualTo("0.00");
        assertThat(r.vatExemptSales()).isEqualTo("0.00");
        assertThat(r.total()).isEqualTo("248.00");
    }

    @Test
    void breakdownAlwaysSums() {
        for (int cents = 1; cents <= 500; cents++) {
            for (int qty = 1; qty <= 7; qty++) {
                var r = OrderTotals.compute(List.of(
                        line(String.valueOf(qty), BigDecimal.valueOf(cents, 2).toPlainString(), "0", TaxClass.VATABLE),
                        line("1", BigDecimal.valueOf(cents, 2).toPlainString(), "0", TaxClass.VAT_EXEMPT)),
                        BigDecimal.valueOf(cents, 2), TaxTreatment.REGULAR);
                assertThat(r.vatableSales().add(r.vat()).add(r.vatExemptSales()).add(r.zeroRatedSales()))
                        .isEqualTo(r.total());
            }
        }
    }

    @Test
    void netOfVat() {
        assertThat(OrderTotals.netOfVat(new BigDecimal("112.00"))).isEqualTo("100.00");
        assertThat(OrderTotals.netOfVat(new BigDecimal("99.99"))).isEqualTo("89.28");
    }
}
