package ph.thecoffeejunkie.crm.files;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class StorageDetectTest {

    static byte[] zip(String... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write("x".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String type(byte[] b, String name) {
        return StorageService.detect(b, name).map(StorageService.Detected::contentType).orElse(null);
    }

    @Test
    void knownTypes() throws IOException {
        assertThat(type("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), "a.pdf")).isEqualTo("application/pdf");
        assertThat(type(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}, "a.jpg")).isEqualTo("image/jpeg");
        assertThat(type(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, "a.png")).isEqualTo("image/png");
        assertThat(type(new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}, "a.webp")).isEqualTo("image/webp");
        assertThat(type(zip("[Content_Types].xml", "word/document.xml"), "a.docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(type(zip("[Content_Types].xml", "xl/workbook.xml"), "a.xlsx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(type("name,qty\nBeans,2\n".getBytes(StandardCharsets.UTF_8), "a.csv")).isEqualTo("text/csv");
        assertThat(StorageService.detect(zip("word/document.xml"), "a.docx").orElseThrow().ext()).isEqualTo(".docx");
    }

    @Test
    void rejected() throws IOException {
        assertThat(type(new byte[]{'M', 'Z', 0, 0}, "invoice.pdf")).isNull();
        assertThat(type(zip("word/document.xml", "word/vbaProject.bin"), "a.docx")).isNull();
        assertThat(type(zip("xl/workbook.xml", "xl/vbaProject.bin"), "a.xlsx")).isNull();
        assertThat(type(zip("readme.txt"), "a.zip")).isNull();
        assertThat(type("a,b\n\0".getBytes(StandardCharsets.UTF_8), "a.csv")).isNull();
        assertThat(type(new byte[]{'a', ',', (byte) 0xC3, (byte) 0x28}, "a.csv")).isNull();
        assertThat(type("just text".getBytes(StandardCharsets.UTF_8), "a.txt")).isNull();
    }

    @Test
    void cleanFileName() {
        assertThat(StorageService.cleanFileName("../../a/b.pdf", ".pdf")).isEqualTo("b.pdf");
        assertThat(StorageService.cleanFileName("C:\\x\\y.pdf", ".pdf")).isEqualTo("y.pdf");
        assertThat(StorageService.cleanFileName("x\r\n.pdf", ".pdf")).isEqualTo("x.pdf");
        assertThat(StorageService.cleanFileName("", ".pdf")).isEqualTo("file.pdf");
        assertThat(StorageService.cleanFileName(null, ".csv")).isEqualTo("file.csv");
        assertThat(StorageService.cleanFileName("a".repeat(300) + ".pdf", ".pdf")).hasSize(255);
    }
}
