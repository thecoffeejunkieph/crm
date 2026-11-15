package ph.thecoffeejunkie.crm.insights;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRangeTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsToCurrentMonth() {
        ReportRange r = ReportRange.parse(null, null, CLOCK);
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void inclusiveBoundsAreManilaMidnights() {
        ReportRange r = ReportRange.parse("2026-10-01", "2026-10-31", CLOCK);
        assertThat(r.start()).isEqualTo(Instant.parse("2026-09-30T16:00:00Z"));
        assertThat(r.end()).isEqualTo(Instant.parse("2026-10-31T16:00:00Z"));
    }

    @Test
    void twentyFourMonthsIsTheLimit() {
        assertThat(ReportRange.parse("2025-01-01", "2026-12-31", CLOCK).to()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertBad(() -> ReportRange.parse("2025-01-01", "2027-01-01", CLOCK));
    }

    @Test
    void rejectsReversedAndMalformed() {
        assertBad(() -> ReportRange.parse("2026-11-02", "2026-11-01", CLOCK));
        assertBad(() -> ReportRange.parse("2026-13-01", null, CLOCK));
        assertBad(() -> ReportRange.parse("x", null, CLOCK));
    }

    @Test
    void monthsSpanYearEnd() {
        assertThat(ReportRange.parse("2025-12-15", "2026-02-03", CLOCK).months())
                .containsExactly(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
    }

    @Test
    void byParsing() {
        assertThat(ReportBy.parse(null)).isEqualTo(ReportBy.MONTH);
        assertThat(ReportBy.parse("product")).isEqualTo(ReportBy.PRODUCT);
        assertBad(() -> ReportBy.parse("x"));
    }

    private static void assertBad(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.REPORT_BAD_RANGE));
    }
}
