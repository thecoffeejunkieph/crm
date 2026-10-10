package ph.thecoffeejunkie.crm.controller;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.request.PosSaleRequest;
import ph.thecoffeejunkie.crm.dto.response.CustomerResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse.EmailStatus;
import ph.thecoffeejunkie.crm.exception.EmailSendException;
import ph.thecoffeejunkie.crm.service.InvoiceEmailService;
import ph.thecoffeejunkie.crm.service.PosSaleService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PosControllerTest {

    private final PosSaleService service = mock(PosSaleService.class);
    private final InvoiceEmailService email = mock(InvoiceEmailService.class);
    private final PosController controller = new PosController(service, email);

    @Test
    void sendsReceiptWhenRequested() {
        when(service.checkout(any())).thenReturn(sale("a@b.c"));

        assertEquals(EmailStatus.SENT, controller.checkout(request(true)).emailStatus());
        verify(email).send(77L);
    }

    @Test
    void emailFailureKeepsSale() {
        when(service.checkout(any())).thenReturn(sale("a@b.c"));
        when(email.send(77L)).thenThrow(new EmailSendException("Failed to send invoice email", new RuntimeException()));

        PosSaleResponse response = controller.checkout(request(true));

        assertEquals(EmailStatus.FAILED, response.emailStatus());
        assertEquals("INV-1", response.invoice().invoiceNumber());
    }

    @Test
    void noCustomerEmailIsNotRequested() {
        when(service.checkout(any())).thenReturn(sale(null));

        assertEquals(EmailStatus.NOT_REQUESTED, controller.checkout(request(true)).emailStatus());
        verifyNoInteractions(email);
    }

    @Test
    void notRequestedWhenUnticked() {
        when(service.checkout(any())).thenReturn(sale("a@b.c"));

        assertEquals(EmailStatus.NOT_REQUESTED, controller.checkout(request(false)).emailStatus());
        verifyNoInteractions(email);
    }

    private static PosSaleRequest request(boolean emailReceipt) {
        return new PosSaleRequest(12L, List.of(), null, null, List.of(), null, emailReceipt);
    }

    private static PosSaleResponse sale(String customerEmail) {
        CustomerResponse customer = new CustomerResponse(12L, "Ana", "Cruz", customerEmail,
                null, null, null, null, null, null, null);
        InvoiceResponse invoice = new InvoiceResponse(77L, "INV-1", null, null, List.of(), customer, null,
                InvoiceStatus.PAID, "Paid", new BigDecimal("600.00"), null, null, null, null, null, null, null,
                null, null, null, List.of(), List.of(), null, SalesChannel.POS);
        return new PosSaleResponse(invoice, BigDecimal.ZERO, EmailStatus.NOT_REQUESTED);
    }
}
