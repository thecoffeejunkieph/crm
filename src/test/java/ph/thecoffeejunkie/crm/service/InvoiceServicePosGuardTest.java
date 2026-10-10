package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoicePaymentRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.util.InvoiceNumberGenerator;
import ph.thecoffeejunkie.crm.util.InvoicePaymentTokenService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvoiceServicePosGuardTest {

    private final InvoiceRepository repository = mock(InvoiceRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final DeliveryOrderService deliveryOrderService = mock(DeliveryOrderService.class);
    private final InvoiceService service = new InvoiceService(repository, mock(InvoiceItemRepository.class),
            mock(InvoicePaymentRepository.class), mock(InvoiceNumberGenerator.class), inventoryService,
            deliveryOrderService, mock(CustomerRepository.class), mock(ProductRepository.class),
            mock(CRMUserRepository.class), mock(StorageService.class), mock(NotificationService.class));

    @BeforeEach
    void setUp() {
        Invoice invoice = new Invoice();
        invoice.setId(9L);
        invoice.setInvoiceNumber("INV-9");
        invoice.setChannel(SalesChannel.POS);
        invoice.setStatus(InvoiceStatus.PAID);
        when(repository.findById(9L)).thenReturn(Optional.of(invoice));
    }

    @Test
    void markPaidRejectsPos() {
        assertRejected(() -> service.markPaid(9L));
    }

    @Test
    void cancelRejectsPos() {
        assertRejected(() -> service.cancel(9L));
    }

    @Test
    void recordPaymentRejectsPos() {
        assertRejected(() -> service.recordPayment(9L, BigDecimal.ONE, PaymentMethod.CASH, null));
    }

    @Test
    void uploadProofRejectsPos() {
        assertRejected(() -> service.uploadProofOfPayment(9L, List.of(), BigDecimal.ONE, PaymentMethod.CASH));
    }

    @Test
    void portalHidesPosInvoices() {
        InvoicePaymentTokenService tokens = mock(InvoicePaymentTokenService.class);
        when(tokens.resolveInvoiceId("t")).thenReturn(9L);
        InvoicePaymentPortalService portal = new InvoicePaymentPortalService(repository, tokens, service,
                mock(DistributedLock.class));

        assertThrows(ResourceNotFoundException.class, () -> portal.getForCustomer(9L, "t"));
    }

    private void assertRejected(Executable call) {
        var ex = assertThrows(InvalidRequestException.class, call);
        assertEquals("Not allowed on POS sales", ex.getMessage());
        verifyNoInteractions(deliveryOrderService, inventoryService);
    }
}
