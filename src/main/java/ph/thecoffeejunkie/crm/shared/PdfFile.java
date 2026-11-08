package ph.thecoffeejunkie.crm.shared;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** A generated document, served so the browser shows it instead of downloading it. */
public record PdfFile(String filename, byte[] bytes) {

    public ResponseEntity<byte[]> inline() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }
}
