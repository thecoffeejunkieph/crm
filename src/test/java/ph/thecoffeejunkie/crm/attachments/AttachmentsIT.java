package ph.thecoffeejunkie.crm.attachments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.attachments.AttachmentsFixture.as;

@IntegrationTest
class AttachmentsIT {

    static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] CSV = "name,qty\nBeans,2\n".getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    AttachmentsFixture f;
    long admin;
    long rep;
    long other;
    long acme;
    long order;
    long invoice;
    long wh;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asOther;

    @BeforeEach
    void seed() {
        f = new AttachmentsFixture(jdbc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        acme = f.customer("acme", rep);
        order = f.order(acme, "CONFIRMED");
        invoice = f.invoice(order, "ISSUED");
        wh = f.warehouse("A");
        f.delivery(order, wh);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        asOther = as(other, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "application/octet-stream", bytes);
    }

    private ResultActions upload(RequestPostProcessor who, String type, long id, MockMultipartFile... files)
            throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/attachments");
        for (MockMultipartFile mf : files) {
            b.file(mf);
        }
        return mvc.perform(b.param("subjectType", type).param("subjectId", String.valueOf(id)).param("note", "From PO")
                .with(csrf(mvc)).with(who));
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from attachment where uploaded_by in (?, ?, ?)", Integer.class,
                admin, rep, other);
    }

    private long firstId(ResultActions r) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$[0].id"))
                .longValue();
    }

    @Test
    void uploadListAndDownload() throws Exception {
        upload(asRep, "CUSTOMER", acme, file("PO 123.pdf", PDF), file("prices.csv", CSV)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
        String list = mvc.perform(get("/api/attachments").param("subjectType", "CUSTOMER")
                        .param("subjectId", String.valueOf(acme)).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fileName", contains("prices.csv", "PO 123.pdf")))
                .andExpect(jsonPath("$[0].uploadedByName").value(org.hamcrest.Matchers.startsWith(f.tag + "rep")))
                .andExpect(jsonPath("$[0].note").value("From PO"))
                .andExpect(jsonPath("$[0].canRemove").value(true))
                .andReturn().getResponse().getContentAsString();
        long pdfId = ((Number) com.jayway.jsonpath.JsonPath.read(list, "$[1].id")).longValue();
        String location = mvc.perform(get("/api/attachments/" + pdfId + "/download").with(asRep))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        HttpResponse<byte[]> res = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(location)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(res.body()).isEqualTo(PDF);
        assertThat(res.headers().firstValue("Content-Disposition").orElse("")).contains("PO%20123.pdf");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ATTACHMENT_ADDED' "
                + "and entity_type = 'CUSTOMER' and entity_id = ?", Integer.class, acme)).isEqualTo(2);
    }

    @Test
    void rejectsWrongTypeTooLargeAndTooMany() throws Exception {
        upload(asRep, "CUSTOMER", acme, file("ok.pdf", PDF), file("invoice.pdf", new byte[]{'M', 'Z', 0, 0}))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("invoice.pdf")));
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(asRep, "CUSTOMER", acme, file("big.pdf", big)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_TOO_LARGE"));
        MockMultipartFile[] eleven = new MockMultipartFile[11];
        for (int i = 0; i < 11; i++) {
            eleven[i] = file("f" + i + ".pdf", PDF);
        }
        upload(asRep, "CUSTOMER", acme, eleven).andExpect(status().isBadRequest());
        assertThat(rows()).isZero();
    }

    @Test
    void scoping() throws Exception {
        upload(asOther, "CUSTOMER", acme, file("a.pdf", PDF)).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments").param("subjectType", "CUSTOMER").param("subjectId", String.valueOf(acme))
                .with(asOther)).andExpect(status().isNotFound());
        RequestPostProcessor asWh = as(f.user("wh", Role.WAREHOUSE), Role.WAREHOUSE, wh);
        upload(asWh, "ORDER", order, file("a.pdf", PDF)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_READ_ONLY"));
        upload(asRep, "ORDER", order, file("a.pdf", PDF)).andExpect(status().isCreated());
        mvc.perform(get("/api/attachments").param("orderId", String.valueOf(order)).with(asWh))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canRemove").value(false));
    }

    @Test
    void orderListingIncludesInvoiceFiles() throws Exception {
        upload(asRep, "ORDER", order, file("order.pdf", PDF)).andExpect(status().isCreated());
        upload(asRep, "INVOICE", invoice, file("invoice.pdf", PDF)).andExpect(status().isCreated());
        mvc.perform(get("/api/attachments").param("orderId", String.valueOf(order)).with(asRep))
                .andExpect(jsonPath("$[*].subjectType", contains("INVOICE", "ORDER")));
    }

    @Test
    void removeRules() throws Exception {
        long mine = firstId(upload(asRep, "CUSTOMER", acme, file("mine.pdf", PDF)));
        long byAdmin = firstId(upload(asAdmin, "CUSTOMER", acme, file("admin.pdf", PDF)));
        mvc.perform(post("/api/attachments/" + byAdmin + "/archive").with(csrf(mvc)).with(asRep))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ATTACHMENT_NOT_YOURS"));
        mvc.perform(post("/api/attachments/" + mine + "/archive").with(csrf(mvc)).with(asOther))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/attachments/" + mine + "/archive").with(csrf(mvc)).with(asRep))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/attachments/" + byAdmin + "/archive").with(csrf(mvc)).with(asAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/attachments").param("subjectType", "CUSTOMER").param("subjectId", String.valueOf(acme))
                .with(asRep)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/attachments/" + mine + "/download").with(asRep)).andExpect(status().isFound());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ATTACHMENT_REMOVED' "
                + "and entity_type = 'CUSTOMER' and entity_id = ?", Integer.class, acme)).isEqualTo(2);
    }

    @Test
    void readOnlySubjects() throws Exception {
        long cancelled = f.order(acme, "CANCELLED");
        upload(asRep, "ORDER", cancelled, file("a.pdf", PDF)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_READ_ONLY"));
    }

    @Test
    void thumbnailOnlyForImages() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        long img = firstId(upload(asRep, "CUSTOMER", acme, file("photo.png", png)));
        long doc = firstId(upload(asRep, "CUSTOMER", acme, file("doc.pdf", PDF)));
        mvc.perform(get("/api/attachments/" + img + "/thumbnail").with(asRep)).andExpect(status().isFound());
        mvc.perform(get("/api/attachments/" + doc + "/thumbnail").with(asRep)).andExpect(status().isNotFound());
    }

    /** Listing a read-only record must not 500 for the people who could otherwise remove its files. */
    @Test
    void readOnlyRecordsStillList() throws Exception {
        upload(asRep, "ORDER", order, file("po.pdf", PDF)).andExpect(status().isCreated());
        upload(asAdmin, "INVOICE", invoice, file("inv.pdf", PDF)).andExpect(status().isCreated());
        jdbc.update("update invoice set status = 'VOID' where id = ?", invoice);
        mvc.perform(get("/api/attachments").param("orderId", String.valueOf(order)).with(asAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.subjectType == 'INVOICE')].canRemove",
                        contains(false))).andExpect(jsonPath("$[?(@.subjectType == 'ORDER')].canRemove", contains(true)));
        jdbc.update("update sales_order set status = 'CANCELLED' where id = ?", order);
        mvc.perform(get("/api/attachments").param("orderId", String.valueOf(order)).with(asRep))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canRemove").value(false));
    }

    /** Downloads end in the extension of the checked type, whatever the upload was called. */
    @Test
    void storedNameUsesTheCheckedType() throws Exception {
        upload(asRep, "CUSTOMER", acme, file("report.hta", PDF)).andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].fileName").value("report.pdf"));
    }

    /** The app has no invoice history: invoice file events land on the order's Activity. */
    @Test
    void invoiceFileEventsShowOnTheOrder() throws Exception {
        upload(asRep, "INVOICE", invoice, file("inv.pdf", PDF)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select summary from audit_event where action = 'ATTACHMENT_ADDED' "
                + "and entity_type = 'ORDER' and entity_id = ?", String.class, order))
                .startsWith("Attached \"inv.pdf\" to invoice ");
    }
}
