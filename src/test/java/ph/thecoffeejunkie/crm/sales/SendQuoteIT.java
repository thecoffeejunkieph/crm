package ph.thecoffeejunkie.crm.sales;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@MailIntegrationTest
class SendQuoteIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender mail;
    @Autowired CacheManager caches;
    @Autowired @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        MailSupport.stub(mail);
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        jdbc.update("update app_setting set value = '15' where key = 'sales.quoteValidityDays'");
        jdbc.update("update app_setting set value = to_jsonb(''::text) where key like 'company.%'");
        caches.getCache("settings").clear();
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
    void sendsPdfToPortalContacts() throws Exception {
        f.contact(customer, "one", true);
        f.contact(customer, "two", true);
        f.contact(customer, "off", false);
        long id = f.createdOrder(asRep, customer, line(product, "3"));
        f.transition(asRep, id, "send-quote", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("QUOTED"))
                .andExpect(jsonPath("$.emailedTo", containsInAnyOrder(f.contactEmail("one"), f.contactEmail("two"))));
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 2);
        String number = jdbc.queryForObject("select number from sales_order where id = ?", String.class, id);
        for (MimeMessage m : sent) {
            assertThat(attachments(m)).containsExactly(number + ".pdf");
        }
    }

    @Test
    void noContactsStillQuoted() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, id, "send-quote", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("QUOTED"))
                .andExpect(jsonPath("$.emailedTo").isEmpty());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
    }

    @Test
    void validityFromSetting() throws Exception {
        jdbc.update("update app_setting set value = '7' where key = 'sales.quoteValidityDays'");
        caches.getCache("settings").clear();
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, id, "send-quote", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.quoteValidUntil")
                        .value(LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(7).toString()))
                .andExpect(jsonPath("$.order.quoteExpired").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions resend(String path) throws Exception {
        return mvc.perform(f.json(post(path), "{}").with(asRep));
    }

    @Test
    void resendMailsTheQuoteAndChangesNothing() throws Exception {
        f.contact(customer, "one", true);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.transition(asRep, id, "send-quote", null).andExpect(status().isOk());
        MailSupport.awaitMail(mail, 1);
        var before = jdbc.queryForMap("select version, quoted_at, quote_valid_until from sales_order where id = ?", id);

        resend("/api/orders/" + id + "/resend-quote").andExpect(status().isOk())
                .andExpect(jsonPath("$.emailedTo", containsInAnyOrder(f.contactEmail("one"))));

        MailSupport.awaitMail(mail, 2);
        assertThat(jdbc.queryForMap("select version, quoted_at, quote_valid_until from sales_order where id = ?", id)).isEqualTo(before);
    }

    @Test
    void resendRefusedUntilSentAndOnceExpired() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        resend("/api/orders/" + id + "/resend-quote").andExpect(status().is4xxClientError());
        f.transition(asRep, id, "send-quote", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.allowedActions", org.hamcrest.Matchers.hasItem("RESEND_QUOTE")));
        jdbc.update("update sales_order set quote_valid_until = current_date - 2 where id = ?", id);
        resend("/api/orders/" + id + "/resend-quote").andExpect(status().is4xxClientError());
    }

    @Test
    void resendMailsAnIssuedInvoiceButNotAVoidOne() throws Exception {
        for (String k : List.of("name", "tin", "address")) {
            jdbc.update("update app_setting set value = to_jsonb('x'::text) where key = ?", "company." + k);
        }
        caches.getCache("settings").clear();
        f.contact(customer, "one", true);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.setStatus(id, "CONFIRMED");
        long invoice = com.jayway.jsonpath.JsonPath.<Number>read(mvc.perform(f.json(post("/api/orders/" + id + "/invoice"), "{\"version\":" + f.version(id) + "}")
                .with(asRep).header("Idempotency-Key", java.util.UUID.randomUUID().toString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id").longValue();
        String number = jdbc.queryForObject("select number from invoice where id = ?", String.class, invoice);
        // Issuing mails it once by itself.
        assertThat(attachments(MailSupport.awaitMail(mail, 1).get(0))).containsExactly(number + ".pdf");

        resend("/api/invoices/" + invoice + "/resend").andExpect(status().isOk())
                .andExpect(jsonPath("$.emailedTo", containsInAnyOrder(f.contactEmail("one"))));

        assertThat(attachments(MailSupport.awaitMail(mail, 2).get(1))).containsExactly(number + ".pdf");
        jdbc.update("update invoice set status = 'VOID' where id = ?", invoice);
        resend("/api/invoices/" + invoice + "/resend").andExpect(status().is4xxClientError());
    }

    @Test
    void issuingWithoutPortalContactsSaysNobodyWasMailed() throws Exception {
        for (String k : List.of("name", "tin", "address")) {
            jdbc.update("update app_setting set value = to_jsonb('x'::text) where key = ?", "company." + k);
        }
        caches.getCache("settings").clear();
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        f.setStatus(id, "CONFIRMED");
        mvc.perform(f.json(post("/api/orders/" + id + "/invoice"), "{\"version\":" + f.version(id) + "}")
                .with(asRep).header("Idempotency-Key", java.util.UUID.randomUUID().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.emailedTo.length()").value(0));
        assertThat(MailSupport.sent(mail)).isEmpty();
    }

    @Test
    void emptyOrderRejected() throws Exception {
        long id = f.createdOrder(asRep, customer);
        f.transition(asRep, id, "send-quote", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"));
    }

    @Test
    void rollbackSendsNoMail() throws Exception {
        f.contact(customer, "one", true);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        mvc.perform(f.json(post("/api/orders/" + id + "/send-quote"), "{\"version\":99}").with(asRep))
                .andExpect(status().isConflict());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).isEmpty();
        assertThat(jdbc.queryForObject("select status from sales_order where id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void quotePdfScoped() throws Exception {
        long other = f.user("oth", Role.SALES);
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        mvc.perform(get("/api/orders/" + id + "/quote.pdf").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + id + "/quote.pdf").with(asRep)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    void quotePdfLeavesOutInternalNotes() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        jdbc.update("update sales_order set notes = 'INTERNAL-ONLY', terms = 'PAY-IN-30' where id = ?", id);
        byte[] pdf = mvc.perform(get("/api/orders/" + id + "/quote.pdf").with(asRep)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains("PAY-IN-30").doesNotContain("INTERNAL-ONLY");
    }
}
