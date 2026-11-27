package ph.thecoffeejunkie.crm.purchasing;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@MailIntegrationTest
class PurchaseOrderMailIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired JavaMailSender mail;
    @Autowired @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long product;

    @BeforeEach
    void seed() {
        MailSupport.stub(mail);
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        wh = f.inv.warehouse("A");
        product = f.inv.product("P1", "PC");
    }

    @AfterEach
    void cleanUp() {
        MailSupport.awaitIdle(executor);
        jdbc.update("update purchase_order set approval_request_id = null where supplier_id in "
                + "(select id from supplier where code like ?)", f.inv.tag + "%");
        jdbc.update("delete from approval_request where requested_by in (select id from app_user where email like ?)",
                f.inv.tag + "%");
        f.cleanUp();
    }

    /** A DRAFT PO of 10 x 10.00 for a new supplier with the given email and credit limit. */
    private long draft(String email, String limit) throws Exception {
        long s = f.supplier("S" + System.nanoTime(), limit == null ? null : new BigDecimal(limit));
        jdbc.update("update supplier set email = ? where id = ?", email, s);
        String json = "{\"supplierId\":" + s + ",\"warehouseId\":" + wh + ",\"lines\":[{\"productId\":" + product
                + ",\"qty\":10,\"unitCost\":10.00}]}";
        String res = mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions act(long id, String action) throws Exception {
        return mvc.perform(post("/api/purchase-orders/" + id + "/" + action).with(csrf(mvc)).with(admin));
    }

    private String number(long id) {
        return jdbc.queryForObject("select number from purchase_order where id = ?", String.class, id);
    }

    private static List<String> attachments(MimeMessage m) throws Exception {
        List<String> names = new ArrayList<>();
        if (m.getContent() instanceof Multipart mp) {
            collect(mp, names);
        }
        return names;
    }

    private static void collect(Multipart mp, List<String> names) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            var part = mp.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                names.add(part.getFileName());
            } else if (part.getContent() instanceof Multipart inner) {
                collect(inner, names);
            }
        }
    }

    @Test
    void sendEmailsSupplier() throws Exception {
        long id = draft("s@x.ph", "1000");
        act(id, "send").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 1);
        assertThat(sent.get(0).getAllRecipients()[0].toString()).isEqualTo("s@x.ph");
        assertThat(attachments(sent.get(0))).containsExactly(number(id) + ".pdf");
    }

    @Test
    void approvalEmailsSupplier() throws Exception {
        long id = draft("s@x.ph", "120"); // 100 PO, no balance: raise the opening balance to push it over
        jdbc.update("update supplier set opening_balance = 50 where id = (select supplier_id from purchase_order where id = ?)", id);
        act(id, "send").andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
        long approval = jdbc.queryForObject("select approval_request_id from purchase_order where id = ?", Long.class, id);
        mvc.perform(post("/api/approvals/" + approval + "/decision").contentType("application/json")
                .content("{\"approve\":true,\"note\":\"ok\"}").with(csrf(mvc)).with(admin)).andExpect(status().isOk());
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 1);
        assertThat(attachments(sent.get(0))).containsExactly(number(id) + ".pdf");
    }

    @Test
    void resendDraftIs409() throws Exception {
        long id = draft("s@x.ph", "1000");
        act(id, "resend").andExpect(status().isConflict());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
    }

    @Test
    void resendWithoutEmailReturnsEmpty() throws Exception {
        long id = draft(null, "1000");
        act(id, "send").andExpect(status().isOk());
        act(id, "resend").andExpect(status().isOk()).andExpect(jsonPath("$.to").isEmpty());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
    }

    @Test
    void resendAudits() throws Exception {
        long id = draft("s@x.ph", "1000");
        act(id, "send").andExpect(status().isOk());
        MailSupport.awaitMail(mail, 1);
        act(id, "resend").andExpect(status().isOk()).andExpect(jsonPath("$.to[0]").value("s@x.ph"));
        MailSupport.awaitMail(mail, 2);
        assertThat(jdbc.queryForList("select summary from audit_event where action = 'PO_SENT' and entity_id = ?",
                String.class, id)).containsExactly("Sent PO " + number(id) + " to s@x.ph", "Sent PO " + number(id) + " to s@x.ph");
    }
}
