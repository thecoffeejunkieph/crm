package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;

import java.util.List;

/** Issue and reissue require an Idempotency-Key: a double-clicked issue must not burn two BIR serial numbers. */
@RestController
class InvoiceController {

    private final InvoiceService invoices;
    private final CurrentActor actor;

    InvoiceController(InvoiceService invoices, CurrentActor actor) {
        this.invoices = invoices;
        this.actor = actor;
    }

    @PostMapping("/api/orders/{id}/invoice")
    @PreAuthorize("@salesPermissions.canWrite()")
    InvoiceResponse issue(@PathVariable long id, @RequestHeader("Idempotency-Key") String key,
                          @Valid @RequestBody TransitionRequest req) {
        return invoices.issue(actor.scope(), id, req);
    }

    @PostMapping("/api/orders/{id}/invoice/reissue")
    @PreAuthorize("@salesPermissions.canWrite()")
    InvoiceResponse reissue(@PathVariable long id, @RequestHeader("Idempotency-Key") String key,
                            @Valid @RequestBody ReissueRequest req) {
        return invoices.reissue(actor.scope(), id, req);
    }

    @PostMapping("/api/invoices/{id}/resend")
    @PreAuthorize("@salesPermissions.canWrite()")
    ResendResponse resend(@PathVariable long id) {
        return invoices.resend(actor.scope(), id);
    }

    @GetMapping("/api/orders/{id}/invoices")
    @PreAuthorize("@salesPermissions.canRead()")
    List<InvoiceResponse> list(@PathVariable long id) {
        return invoices.list(actor.scope(), id);
    }

    @GetMapping("/api/invoices/{id}.pdf")
    @PreAuthorize("@salesPermissions.canRead()")
    ResponseEntity<byte[]> pdf(@PathVariable long id) {
        return invoices.pdf(actor.scope(), id).inline();
    }
}
