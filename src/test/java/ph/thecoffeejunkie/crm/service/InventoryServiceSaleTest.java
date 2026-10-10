package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.constant.StockMovementType;
import ph.thecoffeejunkie.crm.entity.InventoryItem;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.InvoiceItem;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.entity.Warehouse;
import ph.thecoffeejunkie.crm.exception.InsufficientStockException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.InventoryItemRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.repository.StockMovementRepository;
import ph.thecoffeejunkie.crm.repository.WarehouseRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceSaleTest {

    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final WarehouseService warehouseService = mock(WarehouseService.class);
    private final InventoryService service = new InventoryService(items, movements, mock(WarehouseRepository.class),
            warehouseService, mock(ProductRepository.class), mock(CRMUserRepository.class));

    private final Warehouse warehouse = new Warehouse();
    private final Product latte = new Product();

    @BeforeEach
    void setUp() {
        warehouse.setId(1L);
        latte.setId(5L);
        latte.setProductName("Latte");
        when(warehouseService.resolveDefaultWarehouse()).thenReturn(warehouse);
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void sellDecrementsOnHandOnlyAndRecordsSaleMovement() {
        InventoryItem item = stock(10, 3);

        service.sellForPos(invoiceSelling(4));

        assertEquals(6, item.getQuantityOnHand());
        assertEquals(3, item.getQuantityReserved());
        verify(movements).save(argThat(m -> m.getType() == StockMovementType.SALE
                && m.getQuantity() == 4 && "POS".equals(m.getReferenceType()) && m.getReferenceId() == 77L));
    }

    @Test
    void sellMoreThanAvailableThrows() {
        stock(5, 3);

        var ex = assertThrows(InsufficientStockException.class, () -> service.sellForPos(invoiceSelling(3)));

        assertTrue(ex.getMessage().contains("Latte"));
        verify(items, never()).save(any());
    }

    @Test
    void sellWithoutInventoryRecordIsInsufficientStock() {
        when(items.findByProductIdAndWarehouseId(5L, 1L)).thenReturn(Optional.empty());

        var ex = assertThrows(InsufficientStockException.class, () -> service.sellForPos(invoiceSelling(1)));

        assertTrue(ex.getMessage().contains("Latte"));
    }

    private InventoryItem stock(int onHand, int reserved) {
        InventoryItem item = new InventoryItem();
        item.setProduct(latte);
        item.setWarehouse(warehouse);
        item.setQuantityOnHand(onHand);
        item.setQuantityReserved(reserved);
        when(items.findByProductIdAndWarehouseId(5L, 1L)).thenReturn(Optional.of(item));
        return item;
    }

    private Invoice invoiceSelling(int quantity) {
        InvoiceItem line = new InvoiceItem();
        line.setProduct(latte);
        line.setQuantity(quantity);
        Invoice invoice = new Invoice();
        invoice.setId(77L);
        invoice.setInvoiceNumber("INV-1");
        invoice.setInvoiceItems(List.of(line));
        return invoice;
    }
}
