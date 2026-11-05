package ph.thecoffeejunkie.crm.files;

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
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

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
    /** What customers may upload on the public payment page. */
    public static final Set<String> CUSTOMER_PROOF_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

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
            // Don't take the whole API down with storage - uploads fail with IllegalStateException instead.
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
            throw new IllegalStateException("Failed to read uploaded file", e);
        }

        String type = detectType(bytes);
        if (type == null || !allowedTypes.contains(type)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported file type. Allowed types: " + describe(allowedTypes));
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
            throw new IllegalStateException("Failed to store file", e);
        }

        log.info("Stored object {} ({} bytes)", key, bytes.length);
        return key;
    }

    /** A file's real type, decided by its bytes (and, for CSV, its name), never by what the client claims. */
    public record Detected(String contentType, String ext) {
    }

    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Attachments (spec 2026-11-05): PDF, JPEG, PNG, WebP, DOCX, XLSX (no macros), UTF-8 CSV. For tests. */
    public static java.util.Optional<Detected> detect(byte[] bytes, String fileName) {
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("detect", ".bin");
            java.nio.file.Files.write(tmp, bytes);
            return detect(tmp, fileName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to check file", e);
        } finally {
            deleteTemp(tmp);
        }
    }

    /**
     * Decides a file's type from its bytes on disk, never holding it in memory: magic bytes for PDF and images, the
     * zip central directory (no decompression; part names case-insensitive) for Office files, a streamed UTF-8 check
     * for CSV.
     */
    public static java.util.Optional<Detected> detect(java.nio.file.Path file, String fileName) throws IOException {
        byte[] head = new byte[16];
        int n;
        try (var in = java.nio.file.Files.newInputStream(file)) {
            n = in.readNBytes(head, 0, head.length);
        }
        head = java.util.Arrays.copyOf(head, n);
        String image = detectType(head);
        if (image != null) {
            return java.util.Optional.of(new Detected(image, TYPE_INFO.get(image)[0]));
        }
        if (startsWith(head, 0, 'P', 'K', 3, 4)) {
            java.util.Set<String> entries = zipEntries(file);
            if (entries.contains("word/vbaproject.bin") || entries.contains("xl/vbaproject.bin")) {
                return java.util.Optional.empty();
            }
            if (entries.contains("word/document.xml")) {
                return java.util.Optional.of(new Detected(DOCX, ".docx"));
            }
            if (entries.contains("xl/workbook.xml")) {
                return java.util.Optional.of(new Detected(XLSX, ".xlsx"));
            }
            return java.util.Optional.empty();
        }
        if (fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".csv") && isUtf8Text(file)) {
            return java.util.Optional.of(new Detected("text/csv", ".csv"));
        }
        return java.util.Optional.empty();
    }

    /** Central-directory names, lower-cased (Office part names are case-insensitive). Unreadable zip → none. */
    private static java.util.Set<String> zipEntries(java.nio.file.Path file) {
        java.util.Set<String> names = new java.util.HashSet<>();
        try (var z = new java.util.zip.ZipFile(file.toFile())) {
            var it = z.entries();
            while (it.hasMoreElements() && names.size() < 10_000) {
                names.add(it.nextElement().getName().toLowerCase(java.util.Locale.ROOT));
            }
        } catch (IOException | IllegalArgumentException e) {
            return java.util.Set.of();
        }
        return names;
    }

    private static boolean isUtf8Text(java.nio.file.Path file) throws IOException {
        var decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        char[] buf = new char[8192];
        try (var r = new java.io.InputStreamReader(java.nio.file.Files.newInputStream(file), decoder)) {
            for (int n = r.read(buf); n != -1; n = r.read(buf)) {
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\0') {
                        return false;
                    }
                }
            }
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }

    public static void deleteTemp(java.nio.file.Path tmp) {
        if (tmp != null) {
            try {
                java.nio.file.Files.deleteIfExists(tmp);
            } catch (IOException e) {
                log.warn("Could not delete temp file {}", tmp);
            }
        }
    }

    private static final java.util.regex.Pattern HIDDEN = java.util.regex.Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    /**
     * Last path segment, no control or invisible formatting characters (e.g. right-to-left override), ending in the
     * checked type's extension (so {@code report.hta} that is really a PDF downloads as {@code report.pdf}), at most
     * 255 code points with the extension kept.
     */
    public static String cleanFileName(String raw, String ext) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = HIDDEN.matcher(name).replaceAll("").trim();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        boolean jpegAlias = ext.equals(".jpg") && lower.endsWith(".jpeg");
        if (!lower.endsWith(ext) && !jpegAlias) {
            int dot = name.lastIndexOf('.');
            name = (dot > 0 ? name.substring(0, dot) : name) + ext;
        }
        String base = name.substring(0, name.length() - (jpegAlias ? 5 : ext.length())).trim();
        String tail = name.substring(name.length() - (jpegAlias ? 5 : ext.length()));
        if (base.isEmpty()) {
            base = "file";
        }
        int max = 255 - tail.length();
        if (base.codePointCount(0, base.length()) > max) {
            base = base.substring(0, base.offsetByCodePoints(0, max));
        }
        return base + tail;
    }

    /** Streams a file already checked by {@link #detect}. */
    public void put(String key, java.nio.file.Path file, String contentType) {
        try (var in = java.nio.file.Files.newInputStream(file)) {
            long size = java.nio.file.Files.size(file);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, size, -1)
                    .contentType(contentType).build());
            log.info("Stored object {} ({} bytes)", key, size);
        } catch (Exception e) {
            log.error("Failed to store object {}", key, e);
            throw new IllegalStateException("Failed to store file", e);
        }
    }

    /** Stores bytes already validated by {@link #detect}. */
    public void put(String key, byte[] bytes, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType(contentType).build());
        } catch (Exception e) {
            log.error("Failed to store object {}", key, e);
            throw new IllegalStateException("Failed to store file", e);
        }
        log.info("Stored object {} ({} bytes)", key, bytes.length);
    }

    /** A presigned link that downloads under the original name (RFC 5987 encoded, so no header injection). */
    public String presignedDownloadUrl(String key, String fileName) {
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key).expiry(presignExpirySeconds)
                    .extraQueryParams(java.util.Map.of("response-content-disposition",
                            "attachment; filename*=UTF-8''" + encoded))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create download link", e);
        }
    }

    /** For the health check only. */
    boolean bucketExists() throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
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
            throw new IllegalStateException("Failed to create download link", e);
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
