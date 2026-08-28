package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.constant.StockMovementType;
import ph.thecoffeejunkie.crm.dto.request.StockReceiptRequest;
import ph.thecoffeejunkie.crm.dto.request.StockReleaseRequest;
import ph.thecoffeejunkie.crm.dto.request.StockReserveRequest;
import ph.thecoffeejunkie.crm.dto.response.InventoryItemResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.StockMovementResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.InvoiceItem;
import ph.thecoffeejunkie.crm.entity.InventoryItem;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;
import ph.thecoffeejunkie.crm.entity.StockMovement;
import ph.thecoffeejunkie.crm.entity.Warehouse;
import ph.thecoffeejunkie.crm.exception.InsufficientStockException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.exception.StockConflictException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.InventoryItemRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.repository.StockMovementRepository;
import ph.thecoffeejunkie.crm.repository.WarehouseRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;
    private final CRMUserRepository crmUserRepository;

    @Transactional
    public InventoryItemResponse receive(StockReceiptRequest request) {
        log.info("Receiving {} units of product {}...", request.quantity(), request.productId());

        Warehouse warehouse = resolveWarehouse(request.warehouseId());
        InventoryItem item = getOrCreateInventoryItem(request.productId(), warehouse);

        item.setQuantityOnHand(item.getQuantityOnHand() + request.quantity());
        InventoryItem saved = saveWithLockHandling(item);

        recordMovement(saved, StockMovementType.RECEIPT, request.quantity(), "MANUAL", null, request.notes());

        log.info("Received stock for product {}: quantityOnHand now {}", request.productId(), saved.getQuantityOnHand());
        return CustomMapper.toInventoryItemResponse(saved);
    }

    @Transactional
    public InventoryItemResponse reserve(StockReserveRequest request) {
        Warehouse warehouse = resolveWarehouse(request.warehouseId());
        InventoryItem saved = reserve(request.productId(), warehouse, request.quantity(),
                request.referenceType(), request.referenceId());

        return CustomMapper.toInventoryItemResponse(saved);
    }

    @Transactional
    public InventoryItemResponse release(StockReleaseRequest request) {
        Warehouse warehouse = resolveWarehouse(request.warehouseId());
        InventoryItem saved = release(request.productId(), warehouse, request.quantity(),
                request.referenceType(), request.referenceId(), request.notes());

        return CustomMapper.toInventoryItemResponse(saved);
    }

    @Transactional
    public void reserveForQuotation(Quotation quotation) {
        log.info("Reserving stock for quotation {}...", quotation.getQuotationNumber());

        Warehouse warehouse = warehouseService.resolveDefaultWarehouse();
        for (QuotationItem item : quotation.getQuotationItems()) {
            reserve(item.getProduct().getId(), warehouse, item.getQuantity(), "QUOTATION", quotation.getId());
        }

        log.info("Reserved stock for quotation {}", quotation.getQuotationNumber());
    }

    @Transactional
    public void reserveForInvoice(Invoice invoice) {
        log.info("Reserving stock for invoice {}...", invoice.getInvoiceNumber());

        Warehouse warehouse = warehouseService.resolveDefaultWarehouse();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            reserve(item.getProduct().getId(), warehouse, item.getQuantity(), "INVOICE", invoice.getId());
        }

        log.info("Reserved stock for invoice {}", invoice.getInvoiceNumber());
    }

    @Transactional
    public void releaseForInvoice(Invoice invoice) {
        log.info("Releasing reserved stock for cancelled invoice {}...", invoice.getInvoiceNumber());

        Warehouse warehouse = warehouseService.resolveDefaultWarehouse();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            release(item.getProduct().getId(), warehouse, item.getQuantity(),
                    "INVOICE_CANCELLED", invoice.getId(), "Invoice cancelled");
        }

        log.info("Released reserved stock for cancelled invoice {}", invoice.getInvoiceNumber());
    }

    @Transactional
    public void deliverForInvoice(Invoice invoice) {
        log.info("Delivering stock for invoice {}...", invoice.getInvoiceNumber());

        Warehouse warehouse = warehouseService.resolveDefaultWarehouse();
        for (InvoiceItem item : invoice.getInvoiceItems()) {
            deliver(item.getProduct().getId(), warehouse, item.getQuantity(), "INVOICE", invoice.getId());
        }

        log.info("Delivered stock for invoice {}", invoice.getInvoiceNumber());
    }

    public void assertSufficientStock(Long productId, int quantity) {
        Warehouse warehouse = warehouseService.resolveDefaultWarehouse();
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElse(null);

        int available = item != null ? item.getQuantityAvailable() : 0;
        if (available < quantity) {
            throw new InsufficientStockException("Insufficient available stock for product : " + item.getProduct().getProductName()
                    + "\nRequested " + quantity + ", Available : " + available);
        }
    }

    public PageResponse<InventoryItemResponse> listInventory(Long productId, Long warehouseId, PageRequest pageRequest) {
        Page<InventoryItem> page;
        if (productId != null && warehouseId != null) {
            page = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId, pageRequest);
        } else if (productId != null) {
            page = inventoryItemRepository.findByProductId(productId, pageRequest);
        } else if (warehouseId != null) {
            page = inventoryItemRepository.findByWarehouseId(warehouseId, pageRequest);
        } else {
            page = inventoryItemRepository.findAll(pageRequest);
        }

        return new PageResponse<>(
                page.getPageable().getPageNumber() + 1,
                page.getPageable().getPageSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent().stream().map(CustomMapper::toInventoryItemResponse).toList()
        );
    }

    public PageResponse<StockMovementResponse> getMovementHistory(Long productId, PageRequest pageRequest) {
        Page<StockMovement> page = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageRequest);

        return new PageResponse<>(
                page.getPageable().getPageNumber() + 1,
                page.getPageable().getPageSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent().stream().map(CustomMapper::toStockMovementResponse).toList()
        );
    }

    public PageResponse<StockMovementResponse> getRecentMovements(Long warehouseId, PageRequest pageRequest) {
        Page<StockMovement> page = warehouseId != null
                ? stockMovementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageRequest)
                : stockMovementRepository.findAllByOrderByCreatedAtDesc(pageRequest);

        return new PageResponse<>(
                page.getPageable().getPageNumber() + 1,
                page.getPageable().getPageSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent().stream().map(CustomMapper::toStockMovementResponse).toList()
        );
    }

    private InventoryItem reserve(Long productId, Warehouse warehouse, int quantity, String referenceType, Long referenceId) {
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElseThrow(() -> new InsufficientStockException(
                        "No stock on hand for product " + productId + " in warehouse " + warehouse.getName()));

        if (item.getQuantityAvailable() < quantity) {
            throw new InsufficientStockException("Insufficient available stock for product " + productId
                    + ": requested " + quantity + ", available " + item.getQuantityAvailable());
        }

        item.setQuantityReserved(item.getQuantityReserved() + quantity);
        InventoryItem saved = saveWithLockHandling(item);

        recordMovement(saved, StockMovementType.RESERVE, quantity, referenceType, referenceId, null);
        return saved;
    }

    private InventoryItem release(Long productId, Warehouse warehouse, int quantity, String referenceType,
                                   Long referenceId, String notes) {
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory record for product " + productId + " in warehouse " + warehouse.getName()));

        item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - quantity));
        InventoryItem saved = saveWithLockHandling(item);

        recordMovement(saved, StockMovementType.RELEASE, quantity, referenceType, referenceId, notes);
        return saved;
    }

    private InventoryItem deliver(Long productId, Warehouse warehouse, int quantity, String referenceType, Long referenceId) {
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElseThrow(() -> new InsufficientStockException(
                        "No stock on hand for product " + productId + " in warehouse " + warehouse.getName()));

        item.setQuantityOnHand(item.getQuantityOnHand() - quantity);
        item.setQuantityReserved(Math.max(0, item.getQuantityReserved() - quantity));
        InventoryItem saved = saveWithLockHandling(item);

        recordMovement(saved, StockMovementType.DELIVERY, quantity, referenceType, referenceId, null);
        return saved;
    }

    private InventoryItem getOrCreateInventoryItem(Long productId, Warehouse warehouse) {
        return inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouse.getId())
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> {
                                log.warn("Product not found with id: {}", productId);
                                return ResourceNotFoundException.of("Product", productId);
                            });

                    InventoryItem item = new InventoryItem();
                    item.setProduct(product);
                    item.setWarehouse(warehouse);
                    item.setQuantityOnHand(0);
                    item.setQuantityReserved(0);
                    return item;
                });
    }

    private Warehouse resolveWarehouse(Long warehouseId) {
        if (warehouseId != null) {
            return warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> {
                        log.warn("Warehouse not found with id: {}", warehouseId);
                        return ResourceNotFoundException.of("Warehouse", warehouseId);
                    });
        }
        return warehouseService.resolveDefaultWarehouse();
    }

    private InventoryItem saveWithLockHandling(InventoryItem item) {
        try {
            return inventoryItemRepository.save(item);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent stock update detected for inventory item {}", item.getId());
            throw new StockConflictException(
                    "Stock for this product/warehouse was updated concurrently, please retry");
        }
    }

    private void recordMovement(InventoryItem item, StockMovementType type, int quantity, String referenceType,
                                 Long referenceId, String notes) {
        StockMovement movement = new StockMovement();
        movement.setProduct(item.getProduct());
        movement.setWarehouse(item.getWarehouse());
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setQuantityOnHandAfter(item.getQuantityOnHand());
        movement.setQuantityReservedAfter(item.getQuantityReserved());
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNotes(notes);
        movement.setPerformedBy(resolveCurrentUser());

        stockMovementRepository.save(movement);
    }

    private CRMUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return crmUserRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
