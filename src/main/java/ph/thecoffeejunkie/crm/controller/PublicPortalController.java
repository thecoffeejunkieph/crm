package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.PaymentMethod;
import ph.thecoffeejunkie.crm.dto.request.QuotationAcceptRequest;
import ph.thecoffeejunkie.crm.dto.response.PublicInvoiceResponse;
import ph.thecoffeejunkie.crm.dto.response.PublicQuotationResponse;
import ph.thecoffeejunkie.crm.service.InvoicePaymentPortalService;
import ph.thecoffeejunkie.crm.service.QuotationEmailService;

import java.math.BigDecimal;
import java.util.List;

/** Unauthenticated endpoints for the customer pages linked from emails; the token authorizes each call. */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicPortalController {

    private final QuotationEmailService quotationEmailService;
    private final InvoicePaymentPortalService invoicePaymentPortalService;

    @GetMapping("/quotations/{id}")
    public PublicQuotationResponse getQuotation(@PathVariable Long id, @RequestParam String token) {
        return quotationEmailService.getForCustomer(id, token);
    }

    @PostMapping("/quotations/{id}/accept")
    public PublicQuotationResponse acceptQuotation(@PathVariable Long id, @RequestParam String token,
                                                   @RequestBody QuotationAcceptRequest request) {
        return quotationEmailService.acceptByCustomer(id, token, request.email());
    }

    @GetMapping("/invoices/{id}")
    public PublicInvoiceResponse getInvoice(@PathVariable Long id, @RequestParam String token) {
        return invoicePaymentPortalService.getForCustomer(id, token);
    }

    @PostMapping(value = "/invoices/{id}/proof-of-payment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PublicInvoiceResponse uploadProofOfPayment(@PathVariable Long id, @RequestParam String token,
                                                      @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                                      @RequestParam(required = false) BigDecimal amount,
                                                      @RequestParam(required = false) PaymentMethod method) {
        return invoicePaymentPortalService.upload(id, token, files, amount, method);
    }
}
