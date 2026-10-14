package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.util.List;

/** Customer contacts only (URL rule /api/portal/** → CUSTOMER); the service scopes to the contact's company. */
@RestController
@RequestMapping("/api/portal")
class PortalOrderController {

    private final PortalOrderService portal;

    PortalOrderController(PortalOrderService portal) {
        this.portal = portal;
    }

    @GetMapping("/orders")
    PageResponse<PortalOrderSummary> list(PageQuery page) {
        return portal.list(page);
    }

    @GetMapping("/orders/{id}")
    PortalOrderResponse get(@PathVariable long id) {
        return portal.get(id);
    }

    @PostMapping("/orders/{id}/accept")
    PortalOrderResponse accept(@PathVariable long id, @Valid @RequestBody TransitionRequest req) {
        return portal.accept(id, req);
    }

    @PostMapping("/orders/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable long id, @Valid @RequestBody DeclineRequest req) {
        portal.decline(id, req);
    }

    @GetMapping("/orders/{id}/payments")
    List<PortalPaymentResponse> payments(@PathVariable long id) {
        return portal.payments(id);
    }

    @PostMapping(value = "/orders/{id}/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PortalPaymentResponse submitPayment(@PathVariable long id, @RequestHeader("Idempotency-Key") String key,
                                        @Valid @RequestPart("payment") PaymentRequest req,
                                        @RequestPart(value = "proof", required = false) MultipartFile proof) {
        return portal.submitPayment(id, req, proof);
    }

    @PutMapping(value = "/payments/{id}/form-2307", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PortalPaymentResponse attach2307(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return portal.attach2307(id, file);
    }

    @GetMapping("/orders/{id}/quote.pdf")
    ResponseEntity<byte[]> quotePdf(@PathVariable long id) {
        return portal.quotePdf(id).inline();
    }

    @GetMapping("/invoices/{id}.pdf")
    ResponseEntity<byte[]> invoicePdf(@PathVariable long id) {
        return portal.invoicePdf(id).inline();
    }
}
