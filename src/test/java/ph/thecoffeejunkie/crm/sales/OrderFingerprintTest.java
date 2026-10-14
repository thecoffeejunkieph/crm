package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFingerprintTest {

    private static SalesOrder order(String qty, String price) {
        SalesOrder o = new SalesOrder();
        o.customerId = 42L;
        o.paymentTerms = PaymentTerms.NET_30;
        o.shippingFee = new BigDecimal("0.00");
        OrderLine l = new OrderLine();
        l.productId = 7L;
        l.qty = new BigDecimal(qty);
        l.unitPrice = new BigDecimal(price);
        l.discountPct = new BigDecimal("0");
        o.lines.add(l);
        return o;
    }

    @Test
    void sameContentSameValue() {
        assertThat(OrderFingerprint.of(order("2", "112.00"))).isEqualTo(OrderFingerprint.of(order("2.000", "112")));
    }

    @Test
    void anyLineChangeDiffers() {
        long base = OrderFingerprint.of(order("2", "112.00"));
        assertThat(OrderFingerprint.of(order("3", "112.00"))).isNotEqualTo(base);
        assertThat(OrderFingerprint.of(order("2", "112.01"))).isNotEqualTo(base);
        SalesOrder discounted = order("2", "112.00");
        discounted.lines.getFirst().discountPct = new BigDecimal("5");
        assertThat(OrderFingerprint.of(discounted)).isNotEqualTo(base);
        SalesOrder terms = order("2", "112.00");
        terms.paymentTerms = PaymentTerms.NET_60;
        assertThat(OrderFingerprint.of(terms)).isNotEqualTo(base);
    }

    @Test
    void alwaysJsSafe() {
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            long v = OrderFingerprint.of(order(String.valueOf(r.nextInt(1000) + 1), r.nextInt(100000) + ".00"));
            assertThat(v).isBetween(0L, (1L << 52) - 1);
        }
    }
}
