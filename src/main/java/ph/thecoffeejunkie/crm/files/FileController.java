package ph.thecoffeejunkie.crm.files;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.net.URI;

/** The stable link stored keys are exposed as (see {@link StorageService#url}): staff only, 302 to a short-lived presigned URL. */
@RestController
class FileController {

    private final StorageService storage;

    FileController(StorageService storage) {
        this.storage = storage;
    }

    /** Attachments have per-record access through /api/attachments, so this route must not reach them. */
    @GetMapping("/api/v1/files/{*key}")
    ResponseEntity<Void> file(@PathVariable String key) {
        String object = key.substring(1); // {*key} keeps the leading slash
        if (object.isEmpty() || object.startsWith("attachments/") || object.contains("..")) {
            throw new DomainException(ErrorCode.NOT_FOUND, "File not found");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(storage.presignedUrl(object))).build();
    }
}
