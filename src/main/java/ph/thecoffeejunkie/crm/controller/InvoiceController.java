package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.dto.request.InvoiceCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.service.InvoiceEmailService;
import ph.thecoffeejunkie.crm.service.InvoicePaymentPortalService;
import ph.thecoffeejunkie.crm.service.InvoicePdfService;
import ph.thecoffeejunkie.crm.service.InvoiceService;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoiceEmailService invoiceEmailService;
    private final InvoicePaymentPortalService invoicePaymentPortalService;

    @GetMapping
    public PageResponse<InvoiceResponse> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(defaultValue = "invoiceNumber") String sortBy,
                               @RequestParam(defaultValue = "ASC") String sortDirection,
                               @RequestParam(required = false) Long customerId) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1,
                pageSize, Sort.Direction.valueOf(sortDirection.toUpperCase()), sortBy);

        if (customerId != null) {
            return invoiceService.findAllByCustomer(customerId, pageRequest);
        }
        return invoiceService.findAll(pageRequest);
    }

    @PostMapping
    public InvoiceResponse save(@RequestBody InvoiceCreateRequest request) {
        return invoiceService.create(request);
    }

    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable Long id) {
        InvoicePdfService.InvoicePdf pdf = invoicePdfService.generate(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    @PostMapping("/{id}/send-email")
    public InvoiceResponse sendEmail(@PathVariable Long id) {
        return invoiceEmailService.send(id);
    }

    /** Upload links in emails sent before the frontend payment page existed land here. */
    @GetMapping("/{id}/proof-of-payment")
    public ResponseEntity<Void> uploadForm(@PathVariable Long id, @RequestParam String token) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(invoicePaymentPortalService.uploadPageUrl(id, token))).build();
    }

    @PostMapping(value = "/{id}/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceResponse recordPayment(@PathVariable Long id,
                                          @RequestParam BigDecimal amount,
                                          @RequestParam PaymentMethod method,
                                          @RequestParam(value = "file", required = false) MultipartFile file) {
        return invoiceService.recordPayment(id, amount, method, file);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{id}/mark-paid")
    public InvoiceResponse markPaid(@PathVariable Long id) {
        return invoiceService.markPaid(id);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@PathVariable Long id) {
        return invoiceService.cancel(id);
    }
}
