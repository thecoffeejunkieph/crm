package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.shared.CurrentActor;

import java.util.List;

@RestController
class PaymentController {

    private final PaymentService payments;
    private final CurrentActor actor;

    PaymentController(PaymentService payments, CurrentActor actor) {
        this.payments = payments;
        this.actor = actor;
    }

    @GetMapping("/api/orders/{id}/payments")
    @PreAuthorize("@salesPermissions.canRead()")
    List<PaymentResponse> list(@PathVariable long id) {
        return payments.list(actor.scope(), id);
    }

    @PostMapping(value = "/api/orders/{id}/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@salesPermissions.canWrite()")
    PaymentResponse record(@PathVariable long id, @RequestHeader("Idempotency-Key") String key,
                           @Valid @RequestPart("payment") PaymentRequest req,
                           @RequestPart(value = "proof", required = false) MultipartFile proof) {
        return payments.record(actor.scope(), id, req, proof);
    }

    @PostMapping("/api/payments/{id}/verify")
    @PreAuthorize("@salesPermissions.canWrite()")
    PaymentResponse verify(@PathVariable long id, @Valid @RequestBody TransitionRequest req) {
        return payments.verify(actor.scope(), id, req.version());
    }

    @PostMapping("/api/payments/{id}/reject")
    @PreAuthorize("@salesPermissions.canWrite()")
    PaymentResponse reject(@PathVariable long id, @Valid @RequestBody RejectRequest req) {
        return payments.reject(actor.scope(), id, req);
    }

    @PutMapping(value = "/api/payments/{id}/form-2307", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@salesPermissions.canWrite()")
    PaymentResponse attach2307(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return payments.attach2307(actor.scope(), id, file);
    }
}
