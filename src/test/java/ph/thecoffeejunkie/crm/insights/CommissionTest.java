package ph.thecoffeejunkie.crm.insights;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void belowTargetIsZero() {
        assertThat(Commission.of(d("9999.99"), d("10000"), d("5"))).isEqualTo(d("0.00"));
    }

    @Test
    void exactlyAtTargetPays() {
        assertThat(Commission.of(d("10000"), d("10000"), d("5"))).isEqualTo(d("500.00"));
    }

    @Test
    void roundsHalfUp() {
        assertThat(Commission.of(d("10000.10"), d("10000"), d("2.5"))).isEqualTo(d("250.00"));
        assertThat(Commission.of(d("10000.30"), d("10000"), d("2.5"))).isEqualTo(d("250.01"));
    }

    @Test
    void noTargetIsZero() {
        assertThat(Commission.of(d("5000"), null, null)).isEqualTo(d("0.00"));
    }

    @Test
    void zeroTargetIsZero() {
        assertThat(Commission.of(d("5000"), d("0"), d("5"))).isEqualTo(d("0.00"));
    }

    @Test
    void progressNullWithoutTarget() {
        assertThat(Commission.progressPct(d("100"), null)).isNull();
        assertThat(Commission.progressPct(d("100"), d("0"))).isNull();
    }

    @Test
    void progressFloors() {
        assertThat(Commission.progressPct(d("3333"), d("10000"))).isEqualTo(33);
    }

    @Test
    void progressNotCapped() {
        assertThat(Commission.progressPct(d("15000"), d("10000"))).isEqualTo(150);
    }
}
