package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.request.PosPaymentRequest;
import ph.thecoffeejunkie.crm.dto.request.PosSaleItemRequest;
import ph.thecoffeejunkie.crm.dto.request.PosSaleRequest;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.InventoryItem;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.entity.Warehouse;
import ph.thecoffeejunkie.crm.exception.InsufficientStockException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.InventoryItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoicePaymentRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.util.InvoiceNumberGenerator;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PosSaleServiceTest {

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final InvoiceItemRepository invoiceItems = mock(InvoiceItemRepository.class);
    private final InvoicePaymentRepository invoicePayments = mock(InvoicePaymentRepository.class);
    private final InventoryItemRepository inventoryItems = mock(InventoryItemRepository.class);
    private final InvoiceNumberGenerator numberGenerator = mock(InvoiceNumberGenerator.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final WarehouseService warehouseService = mock(WarehouseService.class);
    private final PosSaleService service = new PosSaleService(customers, products, invoices, invoiceItems,
            invoicePayments, inventoryItems, numberGenerator, inventoryService, warehouseService,
            mock(CRMUserRepository.class));

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(12L);
        customer.setFirstName("Ana");
        customer.setLastName("Cruz");
        customer.setEmail("ana@example.com");
        when(customers.findById(12L)).thenReturn(Optional.of(customer));
        when(products.findById(3L)).thenReturn(Optional.of(product(3L, "Latte", "600.00", true)));
        when(products.findById(4L)).thenReturn(Optional.of(product(4L, "Old Blend", "300.00", false)));
        when(numberGenerator.generate()).thenReturn("INV-1");
        when(invoiceItems.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invoicePayments.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invoices.save(any())).thenAnswer(i -> {
            Invoice invoice = i.getArgument(0);
            invoice.setId(77L);
            return invoice;
        });
    }

    @Test
    void pricesOverrideLineDiscountAndSaleDiscount() {
        PosSaleRequest request = sale(
                List.of(new PosSaleItemRequest(3L, 2, new BigDecimal("550.00"), 10, DiscountType.PERCENT)),
                50, DiscountType.FIXED,
                List.of(new PosPaymentRequest(PaymentMethod.CASH, new BigDecimal("500.00"), new BigDecimal("1000.00")),
                        new PosPaymentRequest(PaymentMethod.GCASH, new BigDecimal("440.00"), null)));

        PosSaleResponse r = service.checkout(request);

        assertEquals(new BigDecimal("940.00"), r.invoice().totalAmount());
        assertEquals(new BigDecimal("990.00"), r.invoice().invoiceItems().get(0).total());
        assertEquals(new BigDecimal("550.00"), r.invoice().invoiceItems().get(0).price());
        assertEquals(new BigDecimal("600.00"), r.invoice().invoiceItems().get(0).listPrice());
        assertEquals(new BigDecimal("500.00"), r.change());
        assertEquals(SalesChannel.POS, r.invoice().channel());
        assertEquals(InvoiceStatus.PAID, r.invoice().status());
        assertEquals(2, r.invoice().payments().size());
        assertEquals(PosSaleResponse.EmailStatus.NOT_REQUESTED, r.emailStatus());
        verify(inventoryService).sellForPos(any(Invoice.class));
    }

    @Test
    void defaultsToListPrice() {
        PosSaleResponse r = service.checkout(sale(List.of(line(3L, 1)), null, null, cash("600.00")));

        assertEquals(new BigDecimal("600.00"), r.invoice().totalAmount());
        assertEquals(new BigDecimal("0.00"), r.change());
    }

    @Test
    void fixedLineDiscount() {
        PosSaleResponse r = service.checkout(sale(
                List.of(new PosSaleItemRequest(3L, 2, null, 100, DiscountType.FIXED)), null, null, cash("1100.00")));

        assertEquals(new BigDecimal("1100.00"), r.invoice().totalAmount());
    }

    @Test
    void roundsHalfUp() {
        assertEquals(new BigDecimal("5.03"),
                PosSaleService.applyDiscount(new BigDecimal("10.05"), 50, DiscountType.PERCENT));
    }

    @Test
    void rejectsDiscountAboveGross() {
        assertRejected(() -> service.checkout(sale(
                List.of(new PosSaleItemRequest(3L, 2, null, 1300, DiscountType.FIXED)), null, null, cash("0.01"))));
    }

    @Test
    void rejectsPercentOver100() {
        assertRejected(() -> service.checkout(sale(
                List.of(new PosSaleItemRequest(3L, 1, null, 101, DiscountType.PERCENT)), null, null, cash("0.01"))));
    }

    @Test
    void rejectsInactiveProduct() {
        assertRejected(() -> service.checkout(sale(List.of(line(4L, 1)), null, null, cash("300.00"))));
    }

    @Test
    void rejectsDuplicateProduct() {
        assertRejected(() -> service.checkout(sale(List.of(line(3L, 1), line(3L, 1)), null, null, cash("1200.00"))));
    }

    @Test
    void rejectsShortAndOverPayment() {
        var shortEx = assertThrows(InvalidRequestException.class,
                () -> service.checkout(sale(List.of(line(3L, 1)), null, null, cash("599.99"))));
        var overEx = assertThrows(InvalidRequestException.class,
                () -> service.checkout(sale(List.of(line(3L, 1)), null, null, cash("600.01"))));

        assertEquals("Payments must equal the total", shortEx.getMessage());
        assertEquals("Payments must equal the total", overEx.getMessage());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void rejectsTenderedOnNonCash() {
        assertRejected(() -> service.checkout(sale(List.of(line(3L, 1)), null, null,
                List.of(new PosPaymentRequest(PaymentMethod.GCASH, new BigDecimal("600.00"), new BigDecimal("600.00"))))));
    }

    @Test
    void rejectsTenderedBelowAmount() {
        assertRejected(() -> service.checkout(sale(List.of(line(3L, 1)), null, null,
                List.of(new PosPaymentRequest(PaymentMethod.CASH, new BigDecimal("600.00"), new BigDecimal("500.00"))))));
    }

    @Test
    void rejectsZeroQuantity() {
        assertRejected(() -> service.checkout(sale(List.of(line(3L, 0)), null, null, cash("0.01"))));
    }

    @Test
    void stockFailurePropagates() {
        doThrow(new InsufficientStockException("Insufficient stock for Latte")).when(inventoryService).sellForPos(any());

        assertThrows(InsufficientStockException.class,
                () -> service.checkout(sale(List.of(line(3L, 1)), null, null, cash("600.00"))));
    }

    @Test
    void searchDefaultsMissingInventoryToZero() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        when(warehouseService.resolveDefaultWarehouse()).thenReturn(warehouse);
        Product latte = product(3L, "Latte", "600.00", true);
        Product mocha = product(5L, "Mocha", "650.00", true);
        when(products.searchActive(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(latte, mocha)));
        InventoryItem stock = new InventoryItem();
        stock.setProduct(latte);
        stock.setQuantityOnHand(8);
        stock.setQuantityReserved(2);
        when(inventoryItems.findByWarehouseIdAndProductIdIn(eq(1L), anyCollection())).thenReturn(List.of(stock));

        var page = service.searchProducts("  ", null, PageRequest.of(0, 24));

        verify(products).searchActive(isNull(), isNull(), any());
        assertEquals(6, page.content().get(0).quantityAvailable());
        assertEquals(0, page.content().get(1).quantityAvailable());
    }

    private void assertRejected(Executable call) {
        assertThrows(InvalidRequestException.class, call);
        verifyNoInteractions(inventoryService);
    }

    private static PosSaleRequest sale(List<PosSaleItemRequest> items, Integer discount, DiscountType type,
                                       List<PosPaymentRequest> payments) {
        return new PosSaleRequest(12L, items, discount, type, payments, null, false);
    }

    private static PosSaleItemRequest line(Long productId, int quantity) {
        return new PosSaleItemRequest(productId, quantity, null, null, null);
    }

    private static List<PosPaymentRequest> cash(String amount) {
        return List.of(new PosPaymentRequest(PaymentMethod.CASH, new BigDecimal(amount), null));
    }

    private static Product product(Long id, String name, String price, boolean active) {
        Product product = new Product();
        product.setId(id);
        product.setProductName(name);
        product.setPrice(new BigDecimal(price));
        product.setActive(active);
        return product;
    }
}
