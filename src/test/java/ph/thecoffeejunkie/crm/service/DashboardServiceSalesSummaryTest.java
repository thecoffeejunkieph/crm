package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.dto.response.SalesSummaryResponse;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceSalesSummaryTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final DashboardService service = new DashboardService(
            mock(QuotationRepository.class), mock(CustomerRepository.class), invoices);

    @Test
    void bucketsByDayForShortRangesAndByMonthForLongOnes() {
        LocalDate jan5 = LocalDate.of(2026, 1, 5);
        LocalDate feb2 = LocalDate.of(2026, 2, 2);
        List<Object[]> money = List.of(
                new Object[]{jan5, new BigDecimal("100"), new BigDecimal("40")},
                new Object[]{feb2, new BigDecimal("50"), new BigDecimal("-10")});
        List<Object[]> counts = List.of(new Object[]{jan5, 2L}, new Object[]{feb2, 1L});

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(invoices.findDailyPaidSalesAndProfit(from, to)).thenReturn(money);
        when(invoices.countDailyInvoices(from, to)).thenReturn(counts);

        SalesSummaryResponse monthly = service.getSalesSummary(from, to);
        assertEquals(3, monthly.points().size());
        assertEquals("2026-01", monthly.points().get(0).period());
        assertEquals(0, new BigDecimal("150").compareTo(monthly.grossSales()));
        assertEquals(0, new BigDecimal("30").compareTo(monthly.grossProfit()));
        assertEquals(3, monthly.invoiceCount());
        assertEquals(0, monthly.points().get(2).invoiceCount());

        LocalDate shortTo = LocalDate.of(2026, 1, 31);
        when(invoices.findDailyPaidSalesAndProfit(from, shortTo)).thenReturn(money.subList(0, 1));
        when(invoices.countDailyInvoices(from, shortTo)).thenReturn(counts.subList(0, 1));

        SalesSummaryResponse daily = service.getSalesSummary(from, shortTo);
        assertEquals(31, daily.points().size());
        assertEquals("2026-01-05", daily.points().get(4).period());
        assertEquals(2, daily.points().get(4).invoiceCount());
    }
}
