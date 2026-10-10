package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.request.PosPaymentRequest;
import ph.thecoffeejunkie.crm.dto.request.PosSaleItemRequest;
import ph.thecoffeejunkie.crm.dto.request.PosSaleRequest;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.PosProductResponse;
import ph.thecoffeejunkie.crm.dto.response.PosSaleResponse;
import ph.thecoffeejunkie.crm.entity.CRMUser;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.InventoryItem;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.InvoiceItem;
import ph.thecoffeejunkie.crm.entity.InvoicePayment;
import ph.thecoffeejunkie.crm.entity.Product;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.CRMUserRepository;
import ph.thecoffeejunkie.crm.repository.CustomerRepository;
import ph.thecoffeejunkie.crm.repository.InventoryItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoicePaymentRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.repository.ProductRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.InvoiceNumberGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Walk-in retail sales: priced on the server, paid in full, stock deducted at once.
@Slf4j
@Service
@RequiredArgsConstructor
public class PosSaleService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final InvoicePaymentRepository invoicePayments;
    private final InventoryItemRepository inventoryItems;
    private final InvoiceNumberGenerator numberGenerator;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final CRMUserRepository users;

    public PageResponse<PosProductResponse> searchProducts(String q, Long categoryId, PageRequest pageRequest) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Product> page = products.searchActive(query, categoryId, pageRequest);

        Long warehouseId = warehouseService.resolveDefaultWarehouse().getId();
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> available = ids.isEmpty() ? Map.of()
                : inventoryItems.findByWarehouseIdAndProductIdIn(warehouseId, ids).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), InventoryItem::getQuantityAvailable));

        return new PageResponse<>(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent().stream().map(p -> new PosProductResponse(
                        p.getId(),
                        p.getProductName(),
                        p.getCategory() != null ? p.getCategory().getId() : null,
                        p.getCategory() != null ? p.getCategory().getName() : null,
                        p.getUnit(),
                        p.getPrice(),
                        StorageService.url(p.getPicturePath()),
                        available.getOrDefault(p.getId(), 0))).toList());
    }

    @Transactional
    public PosSaleResponse checkout(PosSaleRequest request) {
        log.info("Checking out POS sale for customer {}...", request.customerId());

        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));
        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidRequestException("Add at least one item");
        }
        if (request.payments() == null || request.payments().isEmpty()) {
            throw new InvalidRequestException("Add at least one payment");
        }

        List<InvoiceItem> lines = priceLines(request.items());
        BigDecimal subtotal = lines.stream().map(InvoiceItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = applyDiscount(subtotal, request.discount(), request.discountType());
        BigDecimal change = validatePayments(request.payments(), total);

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(numberGenerator.generate());
        invoice.setChannel(SalesChannel.POS);
        invoice.setCustomer(customer);
        invoice.setSalesRep(resolveCurrentUser());
        invoice.setInvoiceItems(lines.stream().map(invoiceItems::save).toList());
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(now);
        invoice.setTotalAmount(total);
        invoice.setDiscount(request.discount());
        invoice.setDiscountType(request.discountType());
        invoice.setInvoiceDate(today);
        invoice.setDueDate(today);
        invoice.setPaymentTerms(PaymentTerms.DUE_ON_RECEIPT);
        invoice.setNotes(request.notes());
        Invoice saved = invoices.save(invoice);

        for (PosPaymentRequest p : request.payments()) {
            InvoicePayment payment = new InvoicePayment();
            payment.setInvoice(saved);
            payment.setMethod(p.method());
            payment.setAmount(p.amount().setScale(2, RoundingMode.HALF_UP));
            payment.setAmountTendered(p.amountTendered() == null ? null : p.amountTendered().setScale(2, RoundingMode.HALF_UP));
            payment.setRecordedAt(now);
            saved.getPayments().add(invoicePayments.save(payment));
        }

        // Throws on insufficient stock, rolling back everything above.
        inventoryService.sellForPos(saved);

        log.info("Completed POS sale {} for {}", saved.getInvoiceNumber(), total);
        return new PosSaleResponse(CustomMapper.toInvoiceResponse(saved), change, PosSaleResponse.EmailStatus.NOT_REQUESTED);
    }

    // Net amount after a PERCENT (0-100) or FIXED (0..gross) discount, HALF_UP to centavos.
    static BigDecimal applyDiscount(BigDecimal gross, Integer discount, DiscountType type) {
        if (discount == null || discount == 0) {
            return gross.setScale(2, RoundingMode.HALF_UP);
        }
        if (type == null) {
            throw new InvalidRequestException("Discount type is required");
        }
        BigDecimal value = BigDecimal.valueOf(discount);
        BigDecimal off = switch (type) {
            case PERCENT -> {
                if (discount < 0 || discount > 100) {
                    throw new InvalidRequestException("Percent discount must be between 0 and 100");
                }
                yield gross.multiply(value).divide(HUNDRED);
            }
            case FIXED -> {
                if (discount < 0 || value.compareTo(gross) > 0) {
                    throw new InvalidRequestException("Discount cannot be more than the amount");
                }
                yield value;
            }
        };
        return gross.subtract(off).setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvoiceItem> priceLines(List<PosSaleItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        List<InvoiceItem> lines = new ArrayList<>();
        for (PosSaleItemRequest item : items) {
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new InvalidRequestException("Quantity must be greater than zero");
            }
            if (!seen.add(item.productId())) {
                throw new InvalidRequestException("Each product can appear only once");
            }
            Product product = products.findById(item.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", item.productId()));
            if (!product.isActive()) {
                throw new InvalidRequestException(product.getProductName() + " is no longer sold");
            }
            BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : product.getPrice();
            if (unitPrice.signum() < 0) {
                throw new InvalidRequestException("Price cannot be negative");
            }
            BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));

            InvoiceItem line = new InvoiceItem();
            line.setProduct(product);
            line.setQuantity(item.quantity());
            line.setPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
            line.setListPrice(product.getPrice());
            line.setDiscount(item.discount());
            line.setDiscountType(item.discountType());
            line.setTotal(applyDiscount(gross, item.discount(), item.discountType()));
            lines.add(line);
        }
        return lines;
    }

    // Returns the cash change owed; payments must add up to the total exactly.
    private static BigDecimal validatePayments(List<PosPaymentRequest> payments, BigDecimal total) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal change = BigDecimal.ZERO;
        for (PosPaymentRequest p : payments) {
            if (p.method() == null) {
                throw new InvalidRequestException("Payment method is required");
            }
            if (p.amount() == null || p.amount().signum() <= 0) {
                throw new InvalidRequestException("Payment amount must be greater than zero");
            }
            if (p.amountTendered() != null) {
                if (p.method() != PaymentMethod.CASH) {
                    throw new InvalidRequestException("Amount tendered applies to cash only");
                }
                if (p.amountTendered().compareTo(p.amount()) < 0) {
                    throw new InvalidRequestException("Cash tendered is less than the amount");
                }
                change = change.add(p.amountTendered().subtract(p.amount()));
            }
            paid = paid.add(p.amount());
        }
        if (paid.compareTo(total) != 0) {
            throw new InvalidRequestException("Payments must equal the total");
        }
        return change.setScale(2, RoundingMode.HALF_UP);
    }

    private CRMUser resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return users.findByEmail(authentication.getName()).orElse(null);
    }
}
