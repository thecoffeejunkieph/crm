package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.QuotationCreateRequest;
import ph.thecoffeejunkie.crm.dto.response.InvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.dto.response.QuotationResponse;
import ph.thecoffeejunkie.crm.service.QuotationAcceptanceService;
import ph.thecoffeejunkie.crm.service.QuotationEmailService;
import ph.thecoffeejunkie.crm.service.QuotationPdfService;
import ph.thecoffeejunkie.crm.service.QuotationService;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;
    private final QuotationPdfService quotationPdfService;
    private final QuotationEmailService quotationEmailService;
    private final QuotationAcceptanceService quotationAcceptanceService;

    @GetMapping
    public PageResponse<QuotationResponse> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                               @RequestParam(defaultValue = "10") int pageSize,
                               @RequestParam(defaultValue = "quotationNumber") String sortBy,
                               @RequestParam(defaultValue = "ASC") String sortDirection) {
        return quotationService.findAll(PageRequest.of(pageNumber - 1,
                pageSize, Sort.Direction.valueOf(sortDirection.toUpperCase()), sortBy));
    }

    @PostMapping
    public QuotationResponse save(@RequestBody QuotationCreateRequest request) {
        return quotationService.create(request);
    }

    @GetMapping("/{id}")
    public QuotationResponse findById(@PathVariable Long id) {
        return quotationService.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        quotationService.delete(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable Long id) {
        QuotationPdfService.QuotationPdf pdf = quotationPdfService.generate(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    @PostMapping("/{id}/send-email")
    public QuotationResponse sendEmail(@PathVariable Long id) {
        return quotationEmailService.send(id);
    }

    @PostMapping("/{id}/accept")
    public InvoiceResponse accept(@PathVariable Long id) {
        return quotationAcceptanceService.acceptById(id);
    }

    @GetMapping(value = "/{id}/respond", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> respond(@PathVariable Long id,
                                           @RequestParam String token,
                                           @RequestParam String decision) {
        QuotationEmailService.RespondResult result = quotationEmailService.respond(id, token, decision);
        return ResponseEntity.status(result.status()).body(result.html());
    }
}
