package ph.thecoffeejunkie.crm.service;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.constant.SalesChannel;
import ph.thecoffeejunkie.crm.dto.response.PublicInvoiceResponse;
import ph.thecoffeejunkie.crm.entity.InvoicePayment;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.LinkExpiredException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.InvoiceRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.InvoicePaymentTokenService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Backs the customer-facing "upload proof of payment" page in the frontend, reached via the
 * token-secured link in the invoice email. The token stands in for a login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePaymentPortalService {

    private final InvoiceRepository repository;
    private final InvoicePaymentTokenService tokenService;
    private final InvoiceService invoiceService;
    private final DistributedLock distributedLock;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.company.bank.name}")
    private String bankName;

    @Value("${app.company.bank.account-name}")
    private String bankAccountName;

    @Value("${app.company.bank.account-number}")
    private String bankAccountNumber;

    @Value("${app.company.bank.swift-code}")
    private String bankSwiftCode;

    public String buildUploadUrl(Long invoiceId) {
        return uploadPageUrl(invoiceId, tokenService.generate(invoiceId));
    }

    public String uploadPageUrl(Long invoiceId, String token) {
        return frontendUrl + "/portal/invoices/" + invoiceId + "/payment?token=" + token;
    }

    public PublicInvoiceResponse getForCustomer(Long id, String token) {
        resolveToken(id, token);
        return toPublic(findOrThrow(id));
    }

    public PublicInvoiceResponse upload(Long id, String token, List<MultipartFile> files,
                                        BigDecimal amount, PaymentMethod method) {
        resolveToken(id, token);
        findOrThrow(id);

        // A double-submitted form would otherwise race past uploadProofOfPayment's UNPAID check.
        String lockName = "invoice-proof:" + id;
        if (!distributedLock.tryLock(lockName, Duration.ofMinutes(2))) {
            throw new InvalidRequestException("We're already processing an upload for this invoice. Please refresh in a moment.");
        }
        try {
            invoiceService.uploadProofOfPayment(id, files, amount, method);
        } finally {
            distributedLock.unlock(lockName);
        }
        return toPublic(findOrThrow(id));
    }

    private void resolveToken(Long id, String token) {
        Long tokenInvoiceId;
        try {
            tokenInvoiceId = tokenService.resolveInvoiceId(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected invoice payment link with invalid/expired token for id {}: {}", id, e.getMessage());
            throw new LinkExpiredException();
        }
        if (!tokenInvoiceId.equals(id)) {
            log.warn("Invoice payment token/path id mismatch: token={}, path={}", tokenInvoiceId, id);
            throw new InvalidRequestException("This link is not valid.");
        }
    }

    private Invoice findOrThrow(Long id) {
        // POS receipts have no payment portal.
        return repository.findById(id)
                .filter(invoice -> invoice.getChannel() != SalesChannel.POS)
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", id));
    }

    private PublicInvoiceResponse toPublic(Invoice invoice) {
        return new PublicInvoiceResponse(
                invoice.getInvoiceNumber(),
                NotificationService.customerName(invoice.getCustomer()),
                invoice.getStatus(),
                CustomMapper.resolveInvoiceStatusLabel(invoice),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getPaymentTerms() != null ? invoice.getPaymentTerms().getLabel() : null,
                CustomMapper.toPublicLineItems(invoice),
                invoice.getShippingCharges(),
                invoice.getDiscount(),
                invoice.getDiscountType(),
                invoice.getTotalAmount(),
                invoice.getTotalAmount().subtract(invoice.getPayments().stream()
                        .map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)),
                invoice.getNotes(),
                invoice.getTermsAndConditions(),
                bankName, bankAccountName, bankAccountNumber, bankSwiftCode);
    }
}
