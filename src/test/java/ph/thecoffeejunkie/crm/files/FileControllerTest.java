package ph.thecoffeejunkie.crm.files;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileControllerTest {

    private final StorageService storage = mock(StorageService.class);
    private final FileController controller = new FileController(storage);

    @Test
    void redirectsToPresignedUrl() {
        when(storage.presignedUrl("payments/proof/a.pdf")).thenReturn("http://files/signed");
        var res = controller.file("/payments/proof/a.pdf");
        assertThat(res.getStatusCode().value()).isEqualTo(302);
        assertThat(res.getHeaders().getLocation()).hasToString("http://files/signed");
    }

    @Test
    void neverServesAttachmentsOrWalksPaths() {
        for (String key : new String[]{"/attachments/ORDER/1/a.pdf", "/payments/../attachments/a.pdf", "/"}) {
            assertThatThrownBy(() -> controller.file(key)).isInstanceOf(DomainException.class);
        }
    }
}
