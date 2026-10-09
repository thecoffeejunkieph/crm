package ph.thecoffeejunkie.crm.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.exception.FileStorageException;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stores uploaded files (pictures, proofs of payment/pickup/delivery) in a private MinIO bucket.
 * The database keeps only the object key; responses point at {@code /api/v1/files/{key}}, which
 * redirects an authenticated caller to a short-lived presigned URL (see FileController).
 */
@Slf4j
@Service
public class StorageService {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> PROOF_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final String FILES_PATH = "/api/v1/files/";

    // Ordered so the "Allowed types" message reads the same every time.
    private static final Map<String, String[]> TYPE_INFO = new LinkedHashMap<>();
    static {
        TYPE_INFO.put("image/jpeg", new String[]{".jpg", "JPEG"});
        TYPE_INFO.put("image/png", new String[]{".png", "PNG"});
        TYPE_INFO.put("image/webp", new String[]{".webp", "WEBP"});
        TYPE_INFO.put("application/pdf", new String[]{".pdf", "PDF"});
    }

    private final MinioClient client;
    // Presigned URLs are signed for the host the browser uses, which differs from the
    // in-network endpoint the API talks to (e.g. http://minio:9000 vs https://files.example.com).
    private final MinioClient presignClient;
    private final String bucket;
    private final int presignExpirySeconds;

    public StorageService(@Value("${app.minio.endpoint}") String endpoint,
                          @Value("${app.minio.public-endpoint}") String publicEndpoint,
                          @Value("${app.minio.access-key}") String accessKey,
                          @Value("${app.minio.secret-key}") String secretKey,
                          @Value("${app.minio.bucket}") String bucket,
                          @Value("${app.minio.presign-expiry-seconds:600}") int presignExpirySeconds) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        // Region set explicitly so presigning is pure computation - no lookup call to the public host.
        this.presignClient = MinioClient.builder().endpoint(publicEndpoint).region("us-east-1")
                .credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.presignExpirySeconds = presignExpirySeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created storage bucket {}", bucket);
            }
        } catch (Exception e) {
            // Don't take the whole API down with storage - uploads fail with FileStorageException instead.
            log.error("Storage bucket {} is not reachable; file uploads will fail until it is", bucket, e);
        }
    }

    /**
     * Validates the file by its actual bytes (the client-sent content type is ignored) and stores it
     * under {@code prefix/<uuid>.<ext>}. Returns the object key.
     */
    public String store(String prefix, MultipartFile file, Set<String> allowedTypes) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Failed to read uploaded file", e);
        }

        String type = detectType(bytes);
        if (type == null || !allowedTypes.contains(type)) {
            throw new InvalidRequestException("Unsupported file type. Allowed types: " + describe(allowedTypes));
        }

        String key = prefix + "/" + UUID.randomUUID() + TYPE_INFO.get(type)[0];
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(type)
                    .build());
        } catch (Exception e) {
            log.error("Failed to store object {}", key, e);
            throw new FileStorageException("Failed to store file", e);
        }

        log.info("Stored object {} ({} bytes)", key, bytes.length);
        return key;
    }

    public String presignedUrl(String key) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(presignExpirySeconds)
                    .build());
        } catch (Exception e) {
            throw new FileStorageException("Failed to create download link", e);
        }
    }

    public int presignExpirySeconds() {
        return presignExpirySeconds;
    }

    /** Best-effort: a leftover object is harmless, a failed request because of one is not. */
    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete object {}", key, e);
        }
    }

    /** API path the frontend uses for a stored key; null-safe so mappers can call it unconditionally. */
    public static String url(String key) {
        return key == null || key.isBlank() ? null : FILES_PATH + key;
    }

    static String detectType(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        if (startsWith(b, 0, '%', 'P', 'D', 'F', '-')) return "application/pdf";
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... sig) {
        if (b.length < offset + sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((b[offset + i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Set<String> types) {
        return TYPE_INFO.entrySet().stream()
                .filter(e -> types.contains(e.getKey()))
                .map(e -> e.getValue()[1])
                .collect(Collectors.joining(", "));
    }
}
