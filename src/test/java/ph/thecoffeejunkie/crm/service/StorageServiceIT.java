package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class StorageServiceIT {

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z");

    static StorageService storage;

    @BeforeAll
    static void setUp() {
        storage = new StorageService(MINIO.getS3URL(), MINIO.getS3URL(),
                MINIO.getUserName(), MINIO.getPassword(), "crm-test", 60);
        storage.ensureBucket();
    }

    @Test
    void storesValidatedFileAndServesItThroughPresignedLink() throws Exception {
        // Real PNG signature, but the client lies about the type - the bytes decide.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        var upload = new MockMultipartFile("file", "../../evil name.txt", "text/plain", png);

        String key = storage.store("products/7", upload, StorageService.IMAGE_TYPES);
        assertTrue(key.matches("products/7/[0-9a-f-]{36}\\.png"), key);
        assertEquals("/api/v1/files/" + key, StorageService.url(key));

        var http = HttpClient.newHttpClient();
        var response = http.send(HttpRequest.newBuilder(URI.create(storage.presignedUrl(key))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElse(null));
        assertArrayEquals(png, response.body());

        storage.deleteQuietly(key);
        var gone = http.send(HttpRequest.newBuilder(URI.create(storage.presignedUrl(key))).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(404, gone.statusCode());
    }

    @Test
    void rejectsFilesWhoseBytesAreNotAnAllowedType() {
        var pdf = new MockMultipartFile("file", "x.png", "image/png", "%PDF-1.7 ...".getBytes());
        var text = new MockMultipartFile("file", "x.jpg", "image/jpeg", "hello".getBytes());

        var ex = assertThrows(InvalidRequestException.class,
                () -> storage.store("users/1/avatar", pdf, StorageService.IMAGE_TYPES));
        assertEquals("Unsupported file type. Allowed types: JPEG, PNG, WEBP", ex.getMessage());
        assertThrows(InvalidRequestException.class,
                () -> storage.store("invoices/INV-2026-0001/payments", text, StorageService.PROOF_TYPES));
    }
}
