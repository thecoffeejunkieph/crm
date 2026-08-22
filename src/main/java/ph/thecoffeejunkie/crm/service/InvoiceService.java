package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.entity.InvoiceItem;
import ph.thecoffeejunkie.crm.entity.InvoicePayment;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.entity.QuotationItem;
import ph.thecoffeejunkie.crm.exception.FileStorageException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.InvoiceItemRepository;
import ph.thecoffeejunkie.crm.repository.InvoicePaymentRepository;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.InvoiceNumberGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Map<String, String> ALLOWED_PROOF_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf"
    );

    private final InvoiceRepository repository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final InvoiceNumberGenerator generator;
    private final InventoryService inventoryService;
    private final DeliveryOrderService deliveryOrderService;

    @Value("${app.storage.root-dir}")
    private String storageRootDir;

    @Value("${app.storage.public-path}")
    private String storagePublicPath;

    public InvoiceResponse createFromQuotation(Quotation quotation) {
        log.info("Creating invoice for quotation {}...", quotation.getQuotationNumber());

        Invoice existing = repository.findByQuotationId(quotation.getId()).orElse(null);
        if (existing != null) {
            log.info("Invoice {} already exists for quotation {}, skipping creation",
                    existing.getInvoiceNumber(), quotation.getQuotationNumber());
            return CustomMapper.toInvoiceResponse(existing);
        }

        Invoice invoice = toInvoice(quotation);
        InvoiceResponse response = CustomMapper.toInvoiceResponse(repository.save(invoice));

        log.info("Created invoice {} from quotation {}", response.invoiceNumber(), quotation.getQuotationNumber());
        return response;
    }

    public InvoiceResponse findById(Long id) {
        log.info("Getting invoice with id: {}", id);

        return repository.findById(id)
                .map(CustomMapper::toInvoiceResponse)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });
    }

    public PageResponse<InvoiceResponse> findAll(PageRequest pageRequest) {
        log.info("Getting all invoices...");

        Page<Invoice> invoicePage = repository.findAll(pageRequest);

        log.info("Found {} invoices", invoicePage.getTotalElements());
        return new PageResponse<>(
                invoicePage.getPageable().getPageNumber() + 1,
                invoicePage.getPageable().getPageSize(),
                invoicePage.getTotalPages(),
                invoicePage.getTotalElements(),
                invoicePage.getContent().stream()
                        .map(CustomMapper::toInvoiceResponse)
                        .toList()
        );
    }

    private Invoice toInvoice(Quotation quotation) {
        PaymentTerms paymentTerms = quotation.getPaymentTerms() != null
                ? quotation.getPaymentTerms()
                : PaymentTerms.DUE_ON_RECEIPT;
        LocalDate invoiceDate = LocalDate.now();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generator.generate());
        invoice.setQuotation(quotation);
        invoice.setCustomer(quotation.getCustomer());
        invoice.setSalesRep(quotation.getSalesRep());
        invoice.setInvoiceItems(quotation.getQuotationItems().stream()
                .map(this::toInvoiceItem)
                .map(invoiceItemRepository::save)
                .toList());
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setTotalAmount(quotation.getTotalAmount());
        invoice.setShippingCharges(quotation.getShippingCharges());
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(invoiceDate.plusDays(paymentTerms.getDays()));
        invoice.setPaymentTerms(paymentTerms);
        invoice.setNotes(quotation.getNotes());
        invoice.setTermsAndConditions(quotation.getTermsAndConditions());

        return invoice;
    }

    public InvoiceResponse uploadProofOfPayment(Long id, MultipartFile file) {
        log.info("Uploading proof of payment for invoice with id: {}", id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new InvalidRequestException("Proof of payment cannot be uploaded for an invoice with status "
                    + invoice.getStatus());
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Proof of payment file is required");
        }

        String extension = ALLOWED_PROOF_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidRequestException("Unsupported file type. Allowed types: JPEG, PNG, WEBP, PDF");
        }

        String publicPath = writeProofOfPayment(invoice.getInvoiceNumber(), extension, file);

        invoice.setProofOfPaymentPath(publicPath);
        invoice.setStatus(InvoiceStatus.FOR_PAYMENT_VERIFICATION);
        InvoiceResponse response = CustomMapper.toInvoiceResponse(repository.save(invoice));

        log.info("Received proof of payment for invoice {}", invoice.getInvoiceNumber());
        return response;
    }

    public InvoiceResponse recordPayment(Long id, BigDecimal amount, PaymentMethod method, MultipartFile file) {
        log.info("Recording {} payment of {} for invoice with id: {}", method, amount, id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidRequestException("Cannot record a payment for an invoice that is already paid");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvalidRequestException("Cannot record a payment for a cancelled invoice");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Payment amount must be greater than zero");
        }
        if (method == null) {
            throw new InvalidRequestException("Payment method is required");
        }

        BigDecimal amountPaid = invoice.getPayments().stream()
                .map(InvoicePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getTotalAmount().subtract(amountPaid);

        if (amount.compareTo(balance) > 0) {
            throw new InvalidRequestException("Payment amount exceeds the remaining balance of " + balance);
        }

        String proofOfPaymentPath = null;
        if (file != null && !file.isEmpty()) {
            String extension = ALLOWED_PROOF_CONTENT_TYPES.get(file.getContentType());
            if (extension == null) {
                throw new InvalidRequestException("Unsupported file type. Allowed types: JPEG, PNG, WEBP, PDF");
            }
            int index = invoice.getPayments().size() + 1;
            proofOfPaymentPath = writePaymentProof(invoice.getInvoiceNumber(), index, extension, file);
        }

        InvoicePayment payment = new InvoicePayment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setProofOfPaymentPath(proofOfPaymentPath);
        payment.setRecordedAt(LocalDateTime.now());
        invoicePaymentRepository.save(payment);

        BigDecimal newAmountPaid = amountPaid.add(amount);
        if (invoice.getStatus() == InvoiceStatus.UNPAID && newAmountPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.FOR_PAYMENT_VERIFICATION);
            repository.save(invoice);
        }

        log.info("Recorded {} payment of {} for invoice {}", method, amount, invoice.getInvoiceNumber());
        return repository.findById(id)
                .map(CustomMapper::toInvoiceResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", id));
    }

    public InvoiceResponse markPaid(Long id) {
        log.info("Marking invoice as paid with id: {}", id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidRequestException("Invoice is already marked as paid");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        Invoice saved = repository.save(invoice);
        InvoiceResponse response = CustomMapper.toInvoiceResponse(saved);

        deliveryOrderService.createForInvoice(saved);

        log.info("Marked invoice {} as paid", invoice.getInvoiceNumber());
        return response;
    }

    public InvoiceResponse cancel(Long id) {
        log.info("Cancelling invoice with id: {}", id);

        Invoice invoice = repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Invoice not found with id: {}", id);
                    return ResourceNotFoundException.of("Invoice", id);
                });

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidRequestException("Cannot cancel an invoice that has already been paid");
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvalidRequestException("Invoice is already cancelled");
        }

        inventoryService.releaseForInvoice(invoice);

        invoice.setStatus(InvoiceStatus.CANCELLED);
        InvoiceResponse response = CustomMapper.toInvoiceResponse(repository.save(invoice));

        log.info("Cancelled invoice {}", invoice.getInvoiceNumber());
        return response;
    }

    private String writeProofOfPayment(String invoiceNumber, String extension, MultipartFile file) {
        try {
            Path targetDir = Paths.get(storageRootDir, "invoices", "proof-of-payment");
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(invoiceNumber + extension);
            Files.write(targetFile, file.getBytes());

            return storagePublicPath + "/invoices/proof-of-payment/" + invoiceNumber + extension;
        } catch (IOException e) {
            log.error("Failed to store proof of payment for invoice {}", invoiceNumber, e);
            throw new FileStorageException("Failed to store proof of payment file", e);
        }
    }

    private String writePaymentProof(String invoiceNumber, int index, String extension, MultipartFile file) {
        try {
            Path targetDir = Paths.get(storageRootDir, "invoices", "payments");
            Files.createDirectories(targetDir);

            String fileName = invoiceNumber + "-" + index + extension;
            Path targetFile = targetDir.resolve(fileName);
            Files.write(targetFile, file.getBytes());

            return storagePublicPath + "/invoices/payments/" + fileName;
        } catch (IOException e) {
            log.error("Failed to store payment proof for invoice {}", invoiceNumber, e);
            throw new FileStorageException("Failed to store payment proof file", e);
        }
    }

    private InvoiceItem toInvoiceItem(QuotationItem quotationItem) {
        var invoiceItem = new InvoiceItem();
        invoiceItem.setQuantity(quotationItem.getQuantity());
        invoiceItem.setPrice(quotationItem.getPrice());
        invoiceItem.setDiscount(quotationItem.getDiscount());
        invoiceItem.setTotal(quotationItem.getTotal());
        invoiceItem.setProduct(quotationItem.getProduct());

        return invoiceItem;
    }
}
