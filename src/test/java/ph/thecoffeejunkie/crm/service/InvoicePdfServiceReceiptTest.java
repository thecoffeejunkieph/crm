package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoicePaymentResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.LogoAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InvoicePdfServiceReceiptTest {

    private final InvoicePdfService service = new InvoicePdfService(mock(InvoiceRepository.class), mock(LogoAsset.class));

    @BeforeEach
    void setUp() {
        for (String field : List.of("companyName", "companyAddress", "companyEmail", "companyPhone",
                "bankName", "bankAccountName", "bankAccountNumber", "bankSwiftCode")) {
            ReflectionTestUtils.setField(service, field, "x");
        }
    }

    @Test
    void posInvoiceRendersAsPaidSalesReceipt() {
        String html = service.html(response(SalesChannel.POS, List.of(new InvoicePaymentResponse(1L,
                new BigDecimal("500.00"), PaymentMethod.CASH, "Cash", null, null, new BigDecimal("1000.00")))));

        assertTrue(html.contains("SALES RECEIPT"));
        assertTrue(html.contains("PAID"));
        assertTrue(html.contains("Cash"));
        assertFalse(html.contains("Due Date"));
        assertFalse(html.contains("Payment Terms"));
        assertFalse(html.contains("Bank Details"));
    }

    @Test
    void b2bInvoiceUnchanged() {
        String html = service.html(response(SalesChannel.B2B, List.of()));

        assertTrue(html.contains("<h1>INVOICE</h1>"));
        assertTrue(html.contains("Due Date"));
        assertTrue(html.contains("Bank Details"));
        assertFalse(html.contains("SALES RECEIPT"));
    }

    private static InvoiceResponse response(SalesChannel channel, List<InvoicePaymentResponse> payments) {
        CustomerResponse customer = new CustomerResponse(12L, "Ana", "Cruz", "ana@example.com",
                null, null, null, null, null, null, null);
        return new InvoiceResponse(77L, "INV-1", null, null, List.of(), customer, null, InvoiceStatus.PAID, "Paid",
                new BigDecimal("500.00"), null, null, null, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11),
                PaymentTerms.DUE_ON_RECEIPT, "Due on receipt", null, null, null, List.of(), payments, null, channel);
    }
}
