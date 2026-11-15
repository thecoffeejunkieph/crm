package ph.thecoffeejunkie.crm.insights;

import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;

/** The dashboard month: YYYY-MM in Manila, not in the future and at most 24 months back. */
final class InsightsMonth {

    static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}");

    private InsightsMonth() {
    }

    static YearMonth parse(String raw, Clock clock) {
        return parse(raw, clock, 0);
    }

    /** Targets: as {@link #parse}, but next month too, so the owner can set December's targets in November. */
    static YearMonth parseForPlanning(String raw, Clock clock) {
        return parse(raw, clock, 1);
    }

    private static YearMonth parse(String raw, Clock clock, int monthsAhead) {
        YearMonth now = YearMonth.now(clock.withZone(MANILA));
        if (raw == null || raw.isBlank()) {
            return now;
        }
        YearMonth m;
        try {
            if (!ISO.matcher(raw.trim()).matches()) {
                throw bad();
            }
            m = YearMonth.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw bad();
        }
        if (m.isAfter(now.plusMonths(monthsAhead)) || m.isBefore(now.minusMonths(24))) {
            throw bad();
        }
        return m;
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(MANILA));
    }

    static Instant start(YearMonth m) {
        return m.atDay(1).atStartOfDay(MANILA).toInstant();
    }

    static Instant end(YearMonth m) {
        return start(m.plusMonths(1));
    }

    private static DomainException bad() {
        return new DomainException(ErrorCode.INSIGHTS_BAD_MONTH,
                "Pick a month as YYYY-MM, from 24 months ago up to this month.");
    }
}
