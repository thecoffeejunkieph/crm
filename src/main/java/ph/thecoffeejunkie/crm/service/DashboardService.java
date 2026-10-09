package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.response.CountStat;
import ph.thecoffeejunkie.crm.dto.response.DashboardSummaryResponse;
import ph.thecoffeejunkie.crm.dto.response.MonthlySalesPoint;
import ph.thecoffeejunkie.crm.dto.response.ProductSalesResponse;
import ph.thecoffeejunkie.crm.dto.response.RateStat;
import ph.thecoffeejunkie.crm.dto.response.SalesStat;
import ph.thecoffeejunkie.crm.dto.response.SalesSummaryPoint;
import ph.thecoffeejunkie.crm.dto.response.SalesSummaryResponse;
import ph.thecoffeejunkie.crm.dto.response.TopCustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.TopSalesRepResponse;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String WON_STATUS = "ACCEPTED";
    private static final List<String> OPEN_STATUSES = List.of("DRAFT", "PENDING", "APPROVED", "SENT");
    private static final List<String> RESOLVED_STATUSES = List.of("ACCEPTED", "REJECTED", "EXPIRED");
    private static final int PERIOD_DAYS = 30;
    private static final int CHART_MONTHS = 6;
    private static final int TOP_N = 5;

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Builds the dashboard for [from, to]. Defaults: to = today, from = last 30 days.
     * KPI deltas compare against the equal-length period right before {@code from}.
     */
    public DashboardSummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(PERIOD_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        log.info("Building dashboard summary for {} to {}...", start, end);

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);

        BigDecimal[] current = sumPaidSalesAndProfit(start, end);
        BigDecimal[] previous = sumPaidSalesAndProfit(previousStart, previousEnd);
        SalesStat totalSales = new SalesStat(current[0], percentDelta(current[0], previous[0]));
        SalesStat grossProfit = new SalesStat(current[1], percentDelta(current[1], previous[1]));
        CountStat openDeals = new CountStat(
                quotationRepository.countByStatusInAndQuoteDateBetween(OPEN_STATUSES, start, end), null);
        CountStat newLeads = buildNewLeads(end, start, previousStart, previousEnd);
        RateStat conversionRate = buildConversionRate(end, start, previousStart, previousEnd);

        return new DashboardSummaryResponse(
                totalSales,
                grossProfit,
                openDeals,
                newLeads,
                conversionRate,
                buildSalesPerformance(from == null ? null : start, end),
                buildTopSalesReps(start, end),
                buildTopCustomers(start, end),
                buildSalesByProduct(start, end)
        );
    }

    /**
     * Gross sales and gross profit from PAID invoices, invoice count from non-cancelled ones, in [from, to]
     * (default last 30 days), bucketed by day for ranges up to 31 days, else by month.
     */
    public SalesSummaryResponse getSalesSummary(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(PERIOD_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        boolean daily = ChronoUnit.DAYS.between(start, end) < 31;

        Map<String, BigDecimal[]> money = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        if (daily) {
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                money.put(d.toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
        } else {
            for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
                money.put(m.toString(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            }
        }

        for (Object[] row : invoiceRepository.findDailyPaidSalesAndProfit(start, end)) {
            BigDecimal[] bucket = money.get(bucketKey((LocalDate) row[0], daily));
            bucket[0] = bucket[0].add((BigDecimal) row[1]);
            bucket[1] = bucket[1].add((BigDecimal) row[2]);
        }
        for (Object[] row : invoiceRepository.countDailyInvoices(start, end)) {
            counts.merge(bucketKey((LocalDate) row[0], daily), (Long) row[1], Long::sum);
        }

        List<SalesSummaryPoint> points = money.entrySet().stream()
                .map(e -> new SalesSummaryPoint(e.getKey(), e.getValue()[0], e.getValue()[1],
                        counts.getOrDefault(e.getKey(), 0L)))
                .toList();

        return new SalesSummaryResponse(
                points.stream().map(SalesSummaryPoint::grossSales).reduce(BigDecimal.ZERO, BigDecimal::add),
                points.stream().map(SalesSummaryPoint::grossProfit).reduce(BigDecimal.ZERO, BigDecimal::add),
                points.stream().mapToLong(SalesSummaryPoint::invoiceCount).sum(),
                points);
    }

    private String bucketKey(LocalDate date, boolean daily) {
        return daily ? date.toString() : YearMonth.from(date).toString();
    }

    /** [gross sales, gross profit] from PAID invoices dated within [start, end]. */
    private BigDecimal[] sumPaidSalesAndProfit(LocalDate start, LocalDate end) {
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        for (Object[] row : invoiceRepository.findDailyPaidSalesAndProfit(start, end)) {
            totals[0] = totals[0].add((BigDecimal) row[1]);
            totals[1] = totals[1].add((BigDecimal) row[2]);
        }
        return totals;
    }

    private CountStat buildNewLeads(LocalDate today, LocalDate currentStart,
                                     LocalDate previousStart, LocalDate previousEnd) {
        long current = customerRepository.countByCreatedAtBetween(
                currentStart.atStartOfDay(), today.atTime(LocalTime.MAX));
        long previous = customerRepository.countByCreatedAtBetween(
                previousStart.atStartOfDay(), previousEnd.atTime(LocalTime.MAX));

        return new CountStat(current, percentDelta(BigDecimal.valueOf(current), BigDecimal.valueOf(previous)));
    }

    private RateStat buildConversionRate(LocalDate today, LocalDate currentStart,
                                          LocalDate previousStart, LocalDate previousEnd) {
        long currentWon = quotationRepository.countByStatusAndQuoteDateBetween(WON_STATUS, currentStart, today);
        long currentResolved = quotationRepository
                .countByStatusInAndQuoteDateBetween(RESOLVED_STATUSES, currentStart, today);
        long previousWon = quotationRepository
                .countByStatusAndQuoteDateBetween(WON_STATUS, previousStart, previousEnd);
        long previousResolved = quotationRepository
                .countByStatusInAndQuoteDateBetween(RESOLVED_STATUSES, previousStart, previousEnd);

        double currentRate = rate(currentWon, currentResolved);
        double previousRate = rate(previousWon, previousResolved);

        Double deltaPoints = previousResolved == 0 ? null
                : BigDecimal.valueOf(currentRate - previousRate).setScale(1, RoundingMode.HALF_UP).doubleValue();

        return new RateStat(currentRate, deltaPoints);
    }

    private double rate(long won, long resolved) {
        if (resolved == 0) {
            return 0;
        }
        return BigDecimal.valueOf(won * 100.0 / resolved).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private Double percentDelta(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Monthly chart for the range; with no explicit 'from', keeps the last 6 months up to 'end'. */
    private List<MonthlySalesPoint> buildSalesPerformance(LocalDate from, LocalDate end) {
        LocalDate start = from != null ? from : end.minusMonths(CHART_MONTHS - 1L).withDayOfMonth(1);

        Map<String, BigDecimal> totalsByMonth = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            totalsByMonth.put(m.toString(), BigDecimal.ZERO);
        }

        for (Object[] row : quotationRepository.findMonthlySales(WON_STATUS, start, end)) {
            String month = String.valueOf(row[0]);
            BigDecimal total = (BigDecimal) row[1];
            if (totalsByMonth.containsKey(month)) {
                totalsByMonth.put(month, total);
            }
        }

        return totalsByMonth.entrySet().stream()
                .map(entry -> new MonthlySalesPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<TopSalesRepResponse> buildTopSalesReps(LocalDate start, LocalDate end) {
        return quotationRepository.findTopSalesReps(WON_STATUS, start, end, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new TopSalesRepResponse(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        (BigDecimal) row[3],
                        (Long) row[4]))
                .toList();
    }

    private List<TopCustomerResponse> buildTopCustomers(LocalDate start, LocalDate end) {
        return quotationRepository.findTopCustomers(WON_STATUS, start, end, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new TopCustomerResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (BigDecimal) row[3],
                        (Long) row[4]))
                .toList();
    }

    private List<ProductSalesResponse> buildSalesByProduct(LocalDate start, LocalDate end) {
        return quotationRepository.findSalesByProduct(WON_STATUS, start, end).stream()
                .map(row -> new ProductSalesResponse(
                        (Long) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3]))
                .toList();
    }
}
