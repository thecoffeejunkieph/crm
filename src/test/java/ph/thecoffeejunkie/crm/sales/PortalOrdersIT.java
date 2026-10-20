package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class PortalOrdersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    long rep;
    long customer;
    long contact;
    long product;
    RequestPostProcessor asRep;
    RequestPostProcessor me;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        contact = f.contact(customer, "cee", true);
        product = f.product("A", "112.00", "100.00", "VATABLE");
        asRep = as(rep, Role.SALES);
        me = asContact(contact, customer);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** A QUOTED order valid for 15 days, set up without sending mail. */
    long quoted(long customerId, String... lines) throws Exception {
        long id = f.createdOrder(asRep, customerId, lines);
        jdbc.update("update sales_order set status = 'QUOTED', quoted_at = now(), quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(15), id);
        return id;
    }

    private ResultActions accept(long id, long version) throws Exception {
        return mvc.perform(f.json(post("/api/portal/orders/" + id + "/accept"), "{\"version\":" + version + "}").with(me));
    }

    private int notifications(String type) {
        return jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and type = ?",
                Integer.class, rep, type);
    }

    @Test
    void seesOnlyOwnCompanyNonDraft() throws Exception {
        f.createdOrder(asRep, customer, line(product, "1"));
        long mine = quoted(customer, line(product, "1"));
        long otherCustomer = f.customer("other", rep);
        quoted(otherCustomer, line(product, "1"));
        mvc.perform(get("/api/portal/orders").with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
    }

    @Test
    void otherCompanyOrderIs404() throws Exception {
        long otherCustomer = f.customer("other", rep);
        long theirs = quoted(otherCustomer, line(product, "1"));
        mvc.perform(get("/api/portal/orders/" + theirs).with(me)).andExpect(status().isNotFound());
        accept(theirs, f.version(theirs)).andExpect(status().isNotFound());
    }

    @Test
    void jsonHasNoCostNotesOrRep() throws Exception {
        long id = quoted(customer, line(product, "1"));
        jdbc.update("update sales_order set notes = 'internal secret' where id = ?", id);
        String body = SalesFixture.body(mvc.perform(get("/api/portal/orders/" + id).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAccept").value(true))
                .andExpect(jsonPath("$.lines[0].description").value("Product A")));
        assertThat(body).doesNotContain("internal secret").doesNotContainIgnoringCase("cost")
                .doesNotContain("repId").doesNotContain("repName").doesNotContainIgnoringCase("approval")
                .doesNotContain("productId");
    }

    @Test
    void acceptConfirmsWhenChecksPass() throws Exception {
        long id = quoted(customer, line(product, "1"));
        accept(id, f.version(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(notifications("QUOTE_ACCEPTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select actor_type from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'QUOTE_ACCEPTED'", String.class, id)).isEqualTo("CONTACT");
        assertThat(jdbc.queryForObject("select actor_type from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'CONFIRMED'", String.class, id)).isEqualTo("SYSTEM");
    }

    @Test
    void acceptWithFailingChecksCreatesApprovalsForRep() throws Exception {
        long id = quoted(customer, line(product, "1", "112.00", "20"));
        accept(id, f.version(id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.customerAcceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.canAccept").value(false));
        assertThat(jdbc.queryForList("select type from approval_request where subject_type = 'ORDER' and subject_id = ? "
                + "and status = 'PENDING' and requested_by = ?", String.class, id, rep))
                .containsExactlyInAnyOrder("DISCOUNT", "BELOW_COST");
        assertThat(notifications("QUOTE_ACCEPTED")).isEqualTo(1);
    }

    @Test
    void staleAcceptIs409() throws Exception {
        long id = quoted(customer, line(product, "1"));
        long seen = f.version(id);
        f.update(asRep, id, "", line(product, "2")).andExpect(status().isOk());
        jdbc.update("update sales_order set status = 'QUOTED' where id = ?", id);
        accept(id, seen).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(jdbc.queryForObject("select customer_accepted_at is null from sales_order where id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void expiredAccept422() throws Exception {
        long id = quoted(customer, line(product, "1"));
        jdbc.update("update sales_order set quote_valid_until = ? where id = ?",
                LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1), id);
        accept(id, f.version(id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
    }

    @Test
    void declineReturnsToDraftAndHides() throws Exception {
        long id = quoted(customer, line(product, "1"));
        mvc.perform(f.json(post("/api/portal/orders/" + id + "/decline"),
                        "{\"version\":" + f.version(id) + ",\"reason\":\"Too expensive\"}").with(me))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/portal/orders/" + id).with(me)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from sales_order where id = ?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(notifications("QUOTE_DECLINED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select summary from audit_event where entity_type = 'ORDER' and entity_id = ? "
                + "and action = 'QUOTE_DECLINED'", String.class, id)).contains("Too expensive");
    }

    @Test
    void portalPaymentPendingAndProofRequired() throws Exception {
        long id = quoted(customer, line(product, "1"));
        f.setStatus(id, "CONFIRMED");
        var json = new MockMultipartFile("payment", "", "application/json",
                PaymentsIT.payment("100", "0").getBytes());
        mvc.perform(multipart("/api/portal/orders/" + id + "/payments").file(json)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(me))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/portal/orders/" + id + "/payments").file(json)
                        .file(new MockMultipartFile("proof", "p.pdf", "application/pdf", PaymentsIT.PDF))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(me))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.mine").value(true));
        assertThat(notifications("PAYMENT_SUBMITTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select settled_amount from sales_order where id = ?", String.class, id))
                .isEqualTo("0.00");
    }

    @Test
    void contact2307OnlyOnOwnPayment() throws Exception {
        long id = quoted(customer, line(product, "1"));
        f.setStatus(id, "CONFIRMED");
        long staffPayment = jdbc.queryForObject("insert into payment (order_id, status, method, cash_amount, "
                + "withholding_amount, received_on, submitted_by_type, submitted_by_id) values (?, 'VERIFIED', 'CASH', "
                + "100, 1, current_date, 'USER', ?) returning id", Long.class, id, rep);
        mvc.perform(multipart(HttpMethod.PUT, "/api/portal/payments/" + staffPayment + "/form-2307")
                        .file(new MockMultipartFile("file", "2307.pdf", "application/pdf", PaymentsIT.PDF))
                        .with(csrf(mvc)).with(me))
                .andExpect(status().isNotFound());
    }

    @Test
    void voidInvoicePdfDownloadable() throws Exception {
        long id = quoted(customer, line(product, "1"));
        f.setStatus(id, "CONFIRMED");
        long invoice = jdbc.queryForObject("insert into invoice (number, order_id, order_revision, status, issued_at, "
                + "due_date, customer_snapshot, lines_snapshot, vatable_sales, vat, vat_exempt_sales, zero_rated_sales, "
                + "shipping_fee, total, void_reason, issued_by) values (?, ?, 0, 'VOID', now(), current_date, "
                + "'{\"name\":\"Acme\",\"tin\":\"1\",\"address\":\"x\",\"paymentTerms\":\"Net 30\"}', '[]', 0, 0, 0, 0, 0, 0, "
                + "'Wrong TIN', ?) returning id", Long.class, "SI-" + f.tag, id, rep);
        byte[] pdf = mvc.perform(get("/api/portal/invoices/" + invoice + ".pdf").with(me)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains("VOID");
        mvc.perform(get("/api/portal/orders/" + id + "/quote.pdf").with(me)).andExpect(status().isOk());
    }

    @Test
    void cancelledUnsentDraftStaysHidden() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        jdbc.update("update sales_order set status = 'CANCELLED', cancelled_at = now() where id = ?", id);
        mvc.perform(get("/api/portal/orders").with(me)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/portal/orders/" + id).with(me)).andExpect(status().isNotFound());
    }
}
