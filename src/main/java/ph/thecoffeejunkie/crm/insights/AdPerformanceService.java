package ph.thecoffeejunkie.crm.insights;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.insights.AdPerformance.Row;
import ph.thecoffeejunkie.crm.insights.InsightsQueries.AdCohortRow;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ph.thecoffeejunkie.crm.insights.InsightsQueries.money;

@Service
class AdPerformanceService {

    static final int MAX_MONTHS = 24;
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}");

    private final InsightsQueries queries;
    private final Clock clock;

    AdPerformanceService(InsightsQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /** Defaults to the six months ending this month; at most {@value #MAX_MONTHS} months. */
    @Transactional(readOnly = true)
    AdPerformance report(String rawFrom, String rawTo) {
        YearMonth to = rawTo == null || rawTo.isBlank() ? YearMonth.now(clock.withZone(InsightsMonth.MANILA)) : month(rawTo);
        YearMonth from = rawFrom == null || rawFrom.isBlank() ? to.minusMonths(5) : month(rawFrom);
        if (from.isAfter(to) || ChronoUnit.MONTHS.between(from, to) >= MAX_MONTHS) {
            throw new DomainException(ErrorCode.INSIGHTS_BAD_MONTH, "Pick a range of 1 to " + MAX_MONTHS + " months.");
        }
        Map<YearMonth, List<Row>> byMonth = queries.adCohorts(from, to).stream().collect(Collectors.groupingBy(
                r -> YearMonth.from(r.month()), Collectors.mapping(AdPerformanceService::row, Collectors.toList())));
        List<AdPerformance.Month> months = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            List<Row> rows = byMonth.getOrDefault(m, List.of());
            months.add(new AdPerformance.Month(m.toString(), total(rows), rows));
        }
        return new AdPerformance(months);
    }

    private static YearMonth month(String raw) {
        try {
            if (ISO.matcher(raw.trim()).matches()) {
                return YearMonth.parse(raw.trim());
            }
        } catch (DateTimeParseException ignored) {
            // falls through to the error below
        }
        throw new DomainException(ErrorCode.INSIGHTS_BAD_MONTH, "Pick months as YYYY-MM.");
    }

    private static Row row(AdCohortRow r) {
        return row(r.sourceId(), r.sourceName(), r.spend(), r.leads(), r.newCustomers(), r.cohortValue());
    }

    private static Row total(List<Row> rows) {
        boolean anySpend = rows.stream().anyMatch(r -> r.spend() != null);
        BigDecimal spend = anySpend ? rows.stream().map(Row::spend).filter(s -> s != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        return row(null, null, spend, rows.stream().mapToLong(Row::leads).sum(),
                rows.stream().mapToLong(Row::newCustomers).sum(),
                rows.stream().map(Row::cohortValue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static Row row(Long sourceId, String name, BigDecimal spend, long leads, long customers, BigDecimal value) {
        BigDecimal n = BigDecimal.valueOf(customers);
        return new Row(sourceId, name, spend == null ? null : money(spend), leads, customers, money(value),
                spend == null || customers == 0 ? null : spend.divide(n, 2, RoundingMode.HALF_UP),
                customers == 0 ? null : value.divide(n, 2, RoundingMode.HALF_UP),
                spend == null || spend.signum() == 0 ? null : value.divide(spend, 2, RoundingMode.HALF_UP));
    }
}
