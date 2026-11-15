package ph.thecoffeejunkie.crm.attachments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** If storing the 2nd file fails, nothing is saved and the 1st object is deleted again. */
@IntegrationTest
class AttachmentsAllOrNothingIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean StorageService storage;

    AttachmentsFixture f;

    @AfterEach
    void cleanUp() {
        Mockito.reset(storage);
        if (f != null) {
            f.cleanUp();
        }
    }

    @Test
    void secondPutFailsNothingSaved() throws Exception {
        f = new AttachmentsFixture(jdbc);
        long rep = f.user("rep", Role.SALES);
        long acme = f.customer("acme", rep);
        List<String> putKeys = new ArrayList<>();
        int[] calls = {0};
        Mockito.doAnswer(inv -> {
            putKeys.add(inv.getArgument(0));
            if (++calls[0] == 2) {
                throw new IllegalStateException("MinIO down");
            }
            return inv.callRealMethod();
        }).when(storage).put(anyString(), any(java.nio.file.Path.class), anyString());

        int status = mvc.perform(multipart("/api/attachments")
                .file(AttachmentsIT.file("a.pdf", AttachmentsIT.PDF)).file(AttachmentsIT.file("b.pdf", AttachmentsIT.PDF))
                .param("subjectType", "CUSTOMER").param("subjectId", String.valueOf(acme))
                .with(csrf(mvc)).with(AttachmentsFixture.as(rep, Role.SALES))).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(500);
        assertThat(jdbc.queryForObject("select count(*) from attachment where uploaded_by = ?", Integer.class, rep)).isZero();
        Mockito.verify(storage).deleteQuietly(putKeys.get(0));
    }
}
