package ph.thecoffeejunkie.crm.insights;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

/** A dashboard period in Manila: a week (Monday first), month or year. {@code to} is exclusive. */
record Period(String unit, LocalDate from, LocalDate to) {

    static Period of(YearMonth m) {
        return new Period("month", m.atDay(1), m.plusMonths(1).atDay(1));
    }

    /** {@code unit} week|month|year (default month) holding {@code date} (default today), at most 24 months back. */
    static Period parse(String unit, String date, Clock clock) {
        LocalDate today = InsightsMonth.today(clock);
        LocalDate d;
        try {
            d = date == null || date.isBlank() ? today : LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw bad();
        }
        if (d.isAfter(today) || d.isBefore(today.minusMonths(24).withDayOfMonth(1))) {
            throw bad();
        }
        return switch (unit == null || unit.isBlank() ? "month" : unit.trim()) {
            case "week" -> {
                LocalDate f = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Period("week", f, f.plusWeeks(1));
            }
            case "month" -> of(YearMonth.from(d));
            case "year" -> new Period("year", d.withDayOfYear(1), d.withDayOfYear(1).plusYears(1));
            default -> throw bad();
        };
    }

    Period previous() {
        LocalDate f = switch (unit) {
            case "week" -> from.minusWeeks(1);
            case "year" -> from.minusYears(1);
            default -> from.minusMonths(1);
        };
        return new Period(unit, f, from);
    }

    MapSqlParameterSource params() {
        return new MapSqlParameterSource("start", Timestamp.from(from.atStartOfDay(InsightsMonth.MANILA).toInstant()))
                .addValue("end", Timestamp.from(to.atStartOfDay(InsightsMonth.MANILA).toInstant()));
    }

    private static DomainException bad() {
        return new DomainException(ErrorCode.INSIGHTS_BAD_MONTH,
                "Pick a week, month or year within the last 24 months.");
    }
}
