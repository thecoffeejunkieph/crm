package ph.thecoffeejunkie.crm.insights;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightsMonthTest {

    /** 2026-11-06 00:30 in Manila. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T16:30:00Z"), ZoneOffset.UTC);

    @Test
    void blankIsCurrentManilaMonth() {
        assertThat(InsightsMonth.parse(null, CLOCK)).isEqualTo(YearMonth.of(2026, 11));
        assertThat(InsightsMonth.parse(" ", CLOCK)).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void parsesIso() {
        assertThat(InsightsMonth.parse("2026-03", CLOCK)).isEqualTo(YearMonth.of(2026, 3));
    }

    @Test
    void futureRejected() {
        assertBad("2026-12");
    }

    @Test
    void twentyFiveMonthsBackRejected() {
        assertBad("2024-10");
    }

    @Test
    void twentyFourBackAllowed() {
        assertThat(InsightsMonth.parse("2024-11", CLOCK)).isEqualTo(YearMonth.of(2024, 11));
    }

    @Test
    void malformedRejected() {
        assertBad("2026-3");
        assertBad("abc");
    }

    @Test
    void startIsManilaMidnight() {
        assertThat(InsightsMonth.start(YearMonth.of(2026, 11))).isEqualTo(Instant.parse("2026-10-31T16:00:00Z"));
        assertThat(InsightsMonth.end(YearMonth.of(2026, 11))).isEqualTo(Instant.parse("2026-11-30T16:00:00Z"));
    }

    private static void assertBad(String raw) {
        assertThatThrownBy(() -> InsightsMonth.parse(raw, CLOCK)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INSIGHTS_BAD_MONTH);
    }
}
