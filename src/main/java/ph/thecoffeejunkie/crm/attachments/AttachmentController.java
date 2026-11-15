package ph.thecoffeejunkie.crm.attachments;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attachments")
@PreAuthorize("hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE')")
class AttachmentController {

    private final AttachmentService attachments;
    private final CurrentActor actor;

    AttachmentController(AttachmentService attachments, CurrentActor actor) {
        this.attachments = attachments;
        this.actor = actor;
    }

    /** Either {@code subjectType}+{@code subjectId}, or {@code orderId} for an order and its invoices. */
    @GetMapping
    List<AttachmentResponse> list(@RequestParam(required = false) SubjectType subjectType,
                                  @RequestParam(required = false) Long subjectId,
                                  @RequestParam(required = false) Long orderId) {
        if (orderId != null) {
            return attachments.listForOrder(actor.scope(), orderId);
        }
        if (subjectType == null || subjectId == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Give subjectType and subjectId, or orderId.",
                    Map.of("field", "subjectId"));
        }
        return attachments.list(actor.scope(), subjectType, subjectId);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    List<AttachmentResponse> upload(@RequestParam SubjectType subjectType, @RequestParam long subjectId,
                                    @RequestParam(required = false) String note,
                                    @RequestParam("files") List<MultipartFile> files) {
        return attachments.upload(actor.scope(), subjectType, subjectId, note, files);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable long id) {
        attachments.archive(actor.scope(), id);
    }

    @GetMapping("/{id}/download")
    ResponseEntity<Void> download(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(attachments.downloadUrl(actor.scope(), id)))
                .build();
    }

    @GetMapping("/{id}/thumbnail")
    ResponseEntity<Void> thumbnail(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(attachments.thumbnailUrl(actor.scope(), id)))
                .build();
    }
}
