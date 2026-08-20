package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.service.InvoiceEmailService;
import ph.thecoffeejunkie.crm.service.InvoicePaymentPortalService;
import ph.thecoffeejunkie.crm.service.InvoicePdfService;
import ph.thecoffeejunkie.crm.service.InvoiceService;

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
                               @RequestParam(defaultValue = "ASC") String sortDirection) {
        return invoiceService.findAll(PageRequest.of(pageNumber - 1,
                pageSize, Sort.Direction.valueOf(sortDirection.toUpperCase()), sortBy));
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

    @GetMapping(value = "/{id}/proof-of-payment", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> uploadForm(@PathVariable Long id, @RequestParam String token) {
        InvoicePaymentPortalService.PortalResult result = invoicePaymentPortalService.renderUploadForm(id, token);
        return ResponseEntity.status(result.status()).body(result.html());
    }

    @PostMapping(value = "/{id}/proof-of-payment", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> uploadProof(@PathVariable Long id, @RequestParam String token,
                                               @RequestParam("file") MultipartFile file) {
        InvoicePaymentPortalService.PortalResult result = invoicePaymentPortalService.handleUpload(id, token, file);
        return ResponseEntity.status(result.status()).body(result.html());
    }

    @PostMapping(value = "/{id}/staff-proof-of-payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceResponse uploadProofOfPaymentByStaff(@PathVariable Long id,
                                                         @RequestParam("file") MultipartFile file) {
        return invoiceService.uploadProofOfPayment(id, file);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{id}/mark-paid")
    public InvoiceResponse markPaid(@PathVariable Long id) {
        return invoiceService.markPaid(id);
    }
}
