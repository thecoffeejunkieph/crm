package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.NotificationType;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.Role;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoicePaymentRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.DeliveryOrderNumberGenerator;
import ph.thecoffeejunkie.crm.util.InvoiceNumberGenerator;
import ph.thecoffeejunkie.crm.util.LogoAsset;
import ph.thecoffeejunkie.crm.util.QuotationResponseTokenService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationEventsTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final CRMUser rep = CRMUser.builder().email("rep@x.ph").build();

    @Test
    void acceptNotifiesSalesRep() {
        var invoiceService = mock(InvoiceService.class);
        var invoiceEmailService = mock(InvoiceEmailService.class);
        var service = new QuotationAcceptanceService(mock(QuotationRepository.class), invoiceService,
                invoiceEmailService, mock(InventoryService.class), mock(DistributedLock.class), notifications);
        Quotation quotation = quotation(5L);
        InvoiceResponse created = mock(InvoiceResponse.class);
        when(created.id()).thenReturn(11L);
        InvoiceResponse sent = mock(InvoiceResponse.class);
        when(sent.invoiceNumber()).thenReturn("INV-1");
        when(invoiceService.createFromQuotation(quotation)).thenReturn(created);
        when(invoiceEmailService.send(11L)).thenReturn(sent);

        service.accept(quotation);

        verify(notifications).toOwner(rep, NotificationType.QUOTATION_ACCEPTED, 5L, "Quotation Q-1 accepted",
                "Ana Cruz accepted. Invoice INV-1 was created and emailed.");
    }

    @Test
    void customerRejectNotifiesSalesRep() {
        var tokenService = new QuotationResponseTokenService();
        ReflectionTestUtils.setField(tokenService, "secretKey", "test-quotation-secret-that-is-long-enough!!");
        ReflectionTestUtils.setField(tokenService, "tokenValidityDays", 30);
        var repository = mock(QuotationRepository.class);
        var lock = mock(DistributedLock.class);
        when(lock.tryLock(anyString(), any())).thenReturn(true);
        var service = new QuotationEmailService(repository, mock(QuotationPdfService.class), tokenService,
                mock(QuotationAcceptanceService.class), mock(JavaMailSender.class), mock(LogoAsset.class), lock,
                notifications);
        Quotation quotation = quotation(5L);
        quotation.setStatus("SENT");
        quotation.setExpiryDate(null); // fixture date is in the past; respond would refuse it
        when(repository.findById(5L)).thenReturn(Optional.of(quotation));

        service.respond(5L, tokenService.generate(5L), "REJECT");

        verify(notifications).toOwner(rep, NotificationType.QUOTATION_REJECTED, 5L, "Quotation Q-1 declined",
                "Ana Cruz declined the quotation.");
    }

    @Test
    void expiryNotifiesEachRep() {
        var repository = mock(QuotationRepository.class);
        var lock = mock(DistributedLock.class);
        when(lock.tryLock(anyString(), any())).thenReturn(true);
        Quotation first = quotation(5L);
        Quotation second = quotation(6L);
        second.setQuotationNumber("Q-2");
        when(repository.findByStatusInAndExpiryDateBefore(anyList(), any())).thenReturn(List.of(first, second));

        new QuotationExpiryService(repository, lock, notifications).expireOverdueQuotations();

        verify(notifications).toOwner(rep, NotificationType.QUOTATION_EXPIRED, 5L, "Quotation Q-1 expired",
                "Ana Cruz did not respond by 2026-10-01.");
        verify(notifications).toOwner(rep, NotificationType.QUOTATION_EXPIRED, 6L, "Quotation Q-2 expired",
                "Ana Cruz did not respond by 2026-10-01.");
    }

    @Test
    void proofUploadNotifiesAdmins() {
        var invoices = new InvoiceFixture();
        when(invoices.storage.store(anyString(), any(), any())).thenReturn("key");

        invoices.service.uploadProofOfPayment(11L, file());

        verify(notifications).toRole(Role.ADMIN, NotificationType.PAYMENT_AWAITING_VERIFICATION, 11L,
                "Payment to verify: INV-1", "Ana Cruz's payment for invoice INV-1 is waiting for verification.");
    }

    @Test
    void fullPaymentRecordedNotifiesAdmins() {
        var invoices = new InvoiceFixture();

        invoices.service.recordPayment(11L, new BigDecimal("100"), PaymentMethod.CASH, null);

        verify(notifications).toRole(Role.ADMIN, NotificationType.PAYMENT_AWAITING_VERIFICATION, 11L,
                "Payment to verify: INV-1", "Ana Cruz's payment for invoice INV-1 is waiting for verification.");
    }

    @Test
    void partialPaymentDoesNotNotify() {
        var invoices = new InvoiceFixture();

        invoices.service.recordPayment(11L, new BigDecimal("40"), PaymentMethod.CASH, null);

        verifyNoInteractions(notifications);
    }

    @Test
    void markPaidNotifiesRepAndWarehouse() {
        var invoices = new InvoiceFixture();
        DeliveryOrderResponse order = mock(DeliveryOrderResponse.class);
        when(order.id()).thenReturn(21L);
        when(order.deliveryOrderNumber()).thenReturn("DO-1");
        when(invoices.deliveryOrders.createForInvoice(any())).thenReturn(order);

        invoices.service.markPaid(11L);

        verify(notifications).toOwner(rep, NotificationType.INVOICE_PAID, 11L, "Invoice INV-1 paid",
                "Ana Cruz paid in full. Delivery order DO-1 was created.");
        verify(notifications).toRole(Role.WAREHOUSE, NotificationType.DELIVERY_ORDER_CREATED, 21L,
                "New delivery order DO-1", "For invoice INV-1, Ana Cruz. Prepare it for pickup.");
    }

    @Test
    void deliveredNotifiesRep() {
        var repository = mock(DeliveryOrderRepository.class);
        var storage = mock(StorageService.class);
        when(storage.store(anyString(), any(), any())).thenReturn("key");
        var service = new DeliveryOrderService(repository, mock(InventoryService.class),
                mock(DeliveryOrderNumberGenerator.class), storage, notifications);
        DeliveryOrder order = new DeliveryOrder();
        order.setId(21L);
        order.setDeliveryOrderNumber("DO-1");
        order.setStatus(DeliveryOrderStatus.PICKED_UP);
        order.setInvoice(invoice());
        when(repository.findById(21L)).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markDelivered(21L, List.of(file()));

        verify(notifications).toOwner(rep, NotificationType.DELIVERY_ORDER_DELIVERED, 21L, "Delivered: DO-1",
                "Invoice INV-1 for Ana Cruz was delivered.");
    }

    /** An UNPAID INV-1 for 100.00 owned by rep, behind a fully mocked InvoiceService. */
    private class InvoiceFixture {
        final InvoiceRepository repository = mock(InvoiceRepository.class);
        final StorageService storage = mock(StorageService.class);
        final DeliveryOrderService deliveryOrders = mock(DeliveryOrderService.class);
        final InvoiceService service = new InvoiceService(repository, mock(InvoiceItemRepository.class),
                mock(InvoicePaymentRepository.class), mock(InvoiceNumberGenerator.class), mock(InventoryService.class),
                deliveryOrders, mock(CustomerRepository.class), mock(ProductRepository.class),
                mock(CRMUserRepository.class), storage, notifications);

        InvoiceFixture() {
            Invoice invoice = invoice();
            when(repository.findById(11L)).thenReturn(Optional.of(invoice));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setId(11L);
        invoice.setInvoiceNumber("INV-1");
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setTotalAmount(new BigDecimal("100"));
        invoice.setInvoiceItems(new ArrayList<>());
        invoice.setCustomer(customer());
        invoice.setSalesRep(rep);
        return invoice;
    }

    private Quotation quotation(Long id) {
        Quotation quotation = new Quotation();
        quotation.setId(id);
        quotation.setQuotationNumber("Q-1");
        quotation.setCustomer(customer());
        quotation.setSalesRep(rep);
        quotation.setExpiryDate(LocalDate.of(2026, 10, 1));
        return quotation;
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setFirstName("Ana");
        customer.setLastName("Cruz");
        return customer;
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "proof.png", "image/png", new byte[]{1, 2, 3});
    }
}
