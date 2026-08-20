package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Owns what happens when a quotation is accepted, regardless of whether the acceptance came
 * from the customer-facing email link or a staff member marking it accepted manually - both
 * paths must run the exact same invoice-creation pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationAcceptanceService {

    private static final List<String> RESOLVED_STATUSES = List.of("ACCEPTED", "REJECTED");

    private final QuotationRepository quotationRepository;
    private final InvoiceService invoiceService;
    private final InvoiceEmailService invoiceEmailService;

    public InvoiceResponse acceptById(Long quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> {
                    log.warn("Quotation not found with id: {}", quotationId);
                    return ResourceNotFoundException.of("Quotation", quotationId);
                });

        if (RESOLVED_STATUSES.contains(quotation.getStatus())) {
            throw new InvalidRequestException(
                    "Quotation has already been " + quotation.getStatus().toLowerCase(Locale.ROOT));
        }

        if (quotation.getExpiryDate() != null && quotation.getExpiryDate().isBefore(LocalDate.now())) {
            throw new InvalidRequestException("Quotation has expired");
        }

        return accept(quotation);
    }

    public InvoiceResponse accept(Quotation quotation) {
        InvoiceResponse invoice = invoiceService.createFromQuotation(quotation);
        InvoiceResponse sent = invoiceEmailService.send(invoice.id());

        quotation.setStatus("ACCEPTED");
        quotationRepository.save(quotation);

        log.info("Quotation {} accepted; created and emailed invoice {}",
                quotation.getQuotationNumber(), sent.invoiceNumber());
        return sent;
    }
}
