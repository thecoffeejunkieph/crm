package ph.thecoffeejunkie.crm.insights;

import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** A report period: inclusive Manila dates, at most 24 months long (spec 2026-11-07). */
record ReportRange(LocalDate from, LocalDate to) {

    static ReportRange parse(String from, String to, Clock clock) {
        YearMonth now = YearMonth.from(InsightsMonth.today(clock));
        LocalDate f = date(from, now.atDay(1));
        LocalDate t = date(to, now.atEndOfMonth());
        if (f.isAfter(t) || t.isAfter(f.plusMonths(24).minusDays(1))) {
            throw bad();
        }
        return new ReportRange(f, t);
    }

    Instant start() {
        return from.atStartOfDay(InsightsMonth.MANILA).toInstant();
    }

    Instant end() {
        return to.plusDays(1).atStartOfDay(InsightsMonth.MANILA).toInstant();
    }

    List<YearMonth> months() {
        List<YearMonth> out = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            out.add(m);
        }
        return out;
    }

    private static LocalDate date(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw bad();
        }
    }

    static DomainException bad() {
        return new DomainException(ErrorCode.REPORT_BAD_RANGE,
                "Pick dates as YYYY-MM-DD, with the start on or before the end and at most 24 months apart.");
    }
}
