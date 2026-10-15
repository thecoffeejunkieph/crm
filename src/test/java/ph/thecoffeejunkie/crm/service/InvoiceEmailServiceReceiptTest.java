package ph.thecoffeejunkie.crm.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoicePaymentResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.LogoAsset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceEmailServiceReceiptTest {

    private final InvoiceRepository repository = mock(InvoiceRepository.class);
    private final InvoicePdfService pdfService = mock(InvoicePdfService.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final LogoAsset logoAsset = mock(LogoAsset.class);
    private final InvoiceEmailService service = new InvoiceEmailService(repository, pdfService,
            mock(InvoicePaymentPortalService.class), mailSender, mock(CustomerActivityService.class), logoAsset);

    private final CustomerResponse customer = new CustomerResponse(12L, "Ana", "Cruz", "ana@example.com",
            null, null, null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        for (String field : List.of("companyName", "companyAddress", "companyEmail", "salesEmail",
                "bankName", "bankAccountName", "bankAccountNumber", "bankSwiftCode")) {
            ReflectionTestUtils.setField(service, field, "x");
        }
        when(logoAsset.pngBytes()).thenReturn(new byte[0]);
        when(pdfService.generate(77L)).thenReturn(new InvoicePdfService.InvoicePdf("r.pdf", new byte[0]));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    void posInvoiceGetsReceiptSubject() throws Exception {
        Customer entity = new Customer();
        entity.setId(12L);
        entity.setFirstName("Ana");
        entity.setLastName("Cruz");
        entity.setEmail("ana@example.com");
        Invoice invoice = new Invoice();
        invoice.setId(77L);
        invoice.setInvoiceNumber("INV-1");
        invoice.setChannel(SalesChannel.POS);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setCustomer(entity);
        invoice.setInvoiceItems(List.of());
        when(repository.findById(77L)).thenReturn(Optional.of(invoice));

        service.send(77L);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("Your receipt – INV-1", sent.getValue().getSubject());
    }

    @Test
    void receiptBodyShowsPaymentsNotBillDetails() {
        String html = service.buildHtmlBody(response(SalesChannel.POS, InvoiceStatus.PAID, List.of(
                new InvoicePaymentResponse(1L, new BigDecimal("500.00"), PaymentMethod.CASH, "Cash", null, null,
                        new BigDecimal("1000.00")))));

        assertTrue(html.contains("Paid"));
        assertTrue(html.contains("Cash"));
        assertTrue(html.contains("500.00"));
        assertFalse(html.contains("Due Date"));
        assertFalse(html.contains("Bank Details"));
        assertFalse(html.contains("Upload Proof of Payment"));
        assertFalse(html.contains("Total Amount Due"));
    }

    @Test
    void b2bBodyUnchanged() {
        String html = service.buildHtmlBody(response(SalesChannel.B2B, InvoiceStatus.PAID, List.of()));

        assertTrue(html.contains("Due Date"));
        assertTrue(html.contains("Bank Details"));
        assertTrue(html.contains("Total Amount Due"));
    }

    private InvoiceResponse response(SalesChannel channel, InvoiceStatus status, List<InvoicePaymentResponse> payments) {
        return new InvoiceResponse(77L, "INV-1", null, null, List.of(), customer, null, status, "Paid",
                new BigDecimal("500.00"), null, null, null, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11),
                PaymentTerms.DUE_ON_RECEIPT, "Due on receipt", null, null, null, List.of(), payments, null, channel);
    }
}
