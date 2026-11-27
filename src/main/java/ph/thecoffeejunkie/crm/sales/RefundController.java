package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;

import java.util.List;

@RestController
class RefundController {

    private final RefundService refunds;
    private final CurrentActor actor;

    RefundController(RefundService refunds, CurrentActor actor) {
        this.refunds = refunds;
        this.actor = actor;
    }

    @GetMapping("/api/orders/{id}/refunds")
    @PreAuthorize("@salesPermissions.canRead()")
    List<RefundResponse> list(@PathVariable long id) {
        return refunds.list(actor.scope(), id);
    }

    @GetMapping("/api/orders/{id}/refunds/returnable")
    @PreAuthorize("@salesPermissions.canRead()")
    List<Returnable> returnable(@PathVariable long id) {
        return refunds.returnable(actor.scope(), id);
    }

    @GetMapping("/api/refunds/{id}/credit-memo.pdf")
    @PreAuthorize("@salesPermissions.canRead()")
    org.springframework.http.ResponseEntity<byte[]> creditMemo(@PathVariable long id) {
        return refunds.creditMemoPdf(actor.scope(), id).inline();
    }

    @PostMapping("/api/orders/{id}/refunds")
    @PreAuthorize("@salesPermissions.canWrite()")
    @ResponseStatus(HttpStatus.CREATED)
    RefundResponse request(@PathVariable long id, @Valid @RequestBody RefundRequest req) {
        return refunds.request(actor.scope(), id, req);
    }

    @PostMapping(value = "/api/refunds/{id}/payout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@salesPermissions.canWrite()")
    RefundResponse payout(@PathVariable long id, @RequestHeader("Idempotency-Key") String key,
                          @Valid @RequestPart("payout") RefundPayoutRequest req,
                          @RequestPart(value = "proof", required = false) MultipartFile proof) {
        return refunds.payout(actor.scope(), id, req, proof);
    }

    @PostMapping("/api/refunds/{id}/cancel")
    @PreAuthorize("@salesPermissions.canWrite()")
    RefundResponse cancel(@PathVariable long id, @Valid @RequestBody TransitionRequest req) {
        return refunds.cancel(actor.scope(), id, req.version());
    }
}
