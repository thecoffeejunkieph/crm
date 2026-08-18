package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.response.CountStat;
import ph.thecoffeejunkie.crm.dto.response.DashboardSummaryResponse;
import ph.thecoffeejunkie.crm.dto.response.MonthlySalesPoint;
import ph.thecoffeejunkie.crm.dto.response.RateStat;
import ph.thecoffeejunkie.crm.dto.response.SalesStat;
import ph.thecoffeejunkie.crm.dto.response.TopCustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.TopSalesRepResponse;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
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

    public DashboardSummaryResponse getSummary() {
        log.info("Building dashboard summary...");

        LocalDate today = LocalDate.now();
        LocalDate currentStart = today.minusDays(PERIOD_DAYS - 1L);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(PERIOD_DAYS - 1L);

        SalesStat totalSales = buildTotalSales(today, currentStart, previousStart, previousEnd);
        CountStat openDeals = new CountStat(quotationRepository.countByStatusIn(OPEN_STATUSES), null);
        CountStat newLeads = buildNewLeads(today, currentStart, previousStart, previousEnd);
        RateStat conversionRate = buildConversionRate(today, currentStart, previousStart, previousEnd);

        return new DashboardSummaryResponse(
                totalSales,
                openDeals,
                newLeads,
                conversionRate,
                buildSalesPerformance(),
                buildTopSalesReps(),
                buildTopCustomers()
        );
    }

    private SalesStat buildTotalSales(LocalDate today, LocalDate currentStart,
                                       LocalDate previousStart, LocalDate previousEnd) {
        BigDecimal current = quotationRepository
                .sumTotalAmountByStatusAndQuoteDateBetween(WON_STATUS, currentStart, today);
        BigDecimal previous = quotationRepository
                .sumTotalAmountByStatusAndQuoteDateBetween(WON_STATUS, previousStart, previousEnd);

        return new SalesStat(current, percentDelta(current, previous));
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
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<MonthlySalesPoint> buildSalesPerformance() {
        LocalDate start = LocalDate.now().minusMonths(CHART_MONTHS - 1L).withDayOfMonth(1);

        Map<String, BigDecimal> totalsByMonth = new LinkedHashMap<>();
        for (int i = 0; i < CHART_MONTHS; i++) {
            totalsByMonth.put(YearMonth.from(start.plusMonths(i)).toString(), BigDecimal.ZERO);
        }

        for (Object[] row : quotationRepository.findMonthlySales(WON_STATUS, start)) {
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

    private List<TopSalesRepResponse> buildTopSalesReps() {
        return quotationRepository.findTopSalesReps(WON_STATUS, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new TopSalesRepResponse(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        (BigDecimal) row[3],
                        (Long) row[4]))
                .toList();
    }

    private List<TopCustomerResponse> buildTopCustomers() {
        return quotationRepository.findTopCustomers(WON_STATUS, PageRequest.of(0, TOP_N)).stream()
                .map(row -> new TopCustomerResponse(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (BigDecimal) row[3],
                        (Long) row[4]))
                .toList();
    }
}
