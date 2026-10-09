package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.service.StorageService;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Stable URL for a stored file. Requires a logged-in user, then redirects to a short-lived
 * presigned MinIO URL - so cached API responses never hold an expired link.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;

    @GetMapping("/{*key}")
    public ResponseEntity<Void> redirect(@PathVariable String key) {
        String objectKey = key.startsWith("/") ? key.substring(1) : key;

        // Cache the redirect for half the link's lifetime so the browser never follows a dead link.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(storageService.presignedUrl(objectKey)))
                .cacheControl(CacheControl.maxAge(storageService.presignExpirySeconds() / 2, TimeUnit.SECONDS).cachePrivate())
                .build();
    }
}
