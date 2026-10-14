package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class InvoicesIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    SalesFixture f;
    long admin;
    long rep;
    long customer;
    long product;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    static void companySettings(JdbcTemplate jdbc, CacheManager caches, String name, String tin, String address) {
        jdbc.update("update app_setting set value = to_jsonb(?::text) where key = 'company.name'", name);
        jdbc.update("update app_setting set value = to_jsonb(?::text) where key = 'company.tin'", tin);
        jdbc.update("update app_setting set value = to_jsonb(?::text) where key = 'company.address'", address);
        caches.getCache("settings").clear();
    }

    @BeforeEach
    void seed() {
        companySettings(jdbc, caches, "The Coffee Junkie Inc.", "123-456-789-000", "Makati City");
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        companySettings(jdbc, caches, "", "", "");
    }

    private long confirmedOrder() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "3"));
        f.setStatus(id, "CONFIRMED");
        return id;
    }

    private ResultActions issue(RequestPostProcessor who, long orderId) throws Exception {
        return mvc.perform(f.json(post("/api/orders/" + orderId + "/invoice"), "{\"version\":" + f.version(orderId) + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(who));
    }

    private ResultActions reissue(RequestPostProcessor who, long orderId, String reason) throws Exception {
        return mvc.perform(f.json(post("/api/orders/" + orderId + "/invoice/reissue"),
                        "{\"version\":" + f.version(orderId) + ",\"reason\":\"" + reason + "\"}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(who));
    }

    private String pdfText(RequestPostProcessor who, long invoiceId) throws Exception {
        byte[] pdf = mvc.perform(get("/api/invoices/" + invoiceId + ".pdf").with(who)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return PdfText.of(pdf);
    }

    private static int serial(String number) {
        return Integer.parseInt(number.substring("SI-".length()));
    }

    @Test
    void issueFreezesCustomerAndLines() throws Exception {
        long id = confirmedOrder();
        long invoiceId = SalesFixture.id(issue(asRep, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.total").value(336.00)));
        jdbc.update("update customer set tin = '000-CHANGED' where id = ?", customer);
        jdbc.update("update order_line set description = 'Renamed' where order_id = ?", id);
        String text = pdfText(asRep, invoiceId);
        assertThat(text).contains("SALES INVOICE", "111-222-333", "Product A", "PHP 336.00", "123-456-789-000")
                .doesNotContain("000-CHANGED").doesNotContain("Renamed");
        mvc.perform(get("/api/orders/" + id).with(asRep))
                .andExpect(jsonPath("$.activeInvoice.id").value(invoiceId))
                .andExpect(jsonPath("$.allowedActions[?(@ == 'ISSUE_INVOICE')]").isEmpty())
                .andExpect(jsonPath("$.allowedActions[?(@ == 'REISSUE_INVOICE')]").isNotEmpty());
    }

    @Test
    void dueDateFromTerms() throws Exception {
        long id = confirmedOrder();
        String due = LocalDate.now(ZoneId.of("Asia/Manila")).plusDays(30).toString();
        issue(asRep, id).andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").value(due));
        assertThat(jdbc.queryForObject("select due_date::text from sales_order where id = ?", String.class, id))
                .isEqualTo(due);
    }

    @Test
    void secondIssueIs422() throws Exception {
        long id = confirmedOrder();
        issue(asRep, id).andExpect(status().isOk());
        issue(asRep, id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void draftCannotBeInvoiced() throws Exception {
        long id = f.createdOrder(asRep, customer, line(product, "1"));
        issue(asRep, id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void missingCompanySettings400NamesKeys() throws Exception {
        companySettings(jdbc, caches, "The Coffee Junkie Inc.", "", " ");
        long id = confirmedOrder();
        String body = SalesFixture.body(issue(asRep, id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")));
        List<String> fields = JsonPath.read(body, "$.errors[*].field");
        assertThat(fields).containsExactlyInAnyOrder("company.tin", "company.address");
    }

    @Test
    void salesReissueNeedsInvoiceVoidApproval() throws Exception {
        long id = confirmedOrder();
        long invoiceId = SalesFixture.id(issue(asRep, id));
        reissue(asRep, id, "Wrong TIN").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.approvals[0].approvalType").value("INVOICE_VOID"))
                .andExpect(jsonPath("$.approvals[0].subjectType").value("INVOICE"))
                .andExpect(jsonPath("$.approvals[0].subjectId").value(invoiceId));
    }

    @Test
    void reissueVoidsAndKeepsOldNumber() throws Exception {
        long id = confirmedOrder();
        String first = SalesFixture.body(issue(asRep, id));
        long oldId = ((Number) JsonPath.read(first, "$.id")).longValue();
        String oldNumber = JsonPath.read(first, "$.number");
        String second = SalesFixture.body(reissue(asAdmin, id, "Wrong TIN").andExpect(status().isOk()));
        long newId = ((Number) JsonPath.read(second, "$.id")).longValue();
        assertThat(serial(JsonPath.read(second, "$.number"))).isEqualTo(serial(oldNumber) + 1);
        mvc.perform(get("/api/orders/" + id + "/invoices").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newId))
                .andExpect(jsonPath("$[1].number").value(oldNumber))
                .andExpect(jsonPath("$[1].status").value("VOID"))
                .andExpect(jsonPath("$[1].voidReason").value("Wrong TIN"))
                .andExpect(jsonPath("$[1].replacedById").value(newId));
        assertThat(jdbc.queryForObject("select count(*) from invoice where id = ?", Integer.class, oldId)).isEqualTo(1);
    }

    @Test
    void voidPdfShowsVoid() throws Exception {
        long id = confirmedOrder();
        long oldId = SalesFixture.id(issue(asRep, id));
        reissue(asAdmin, id, "Wrong TIN").andExpect(status().isOk());
        assertThat(pdfText(asRep, oldId)).contains("VOID", "Wrong TIN");
    }

    @Test
    void missingIdempotencyKey400() throws Exception {
        long id = confirmedOrder();
        mvc.perform(f.json(post("/api/orders/" + id + "/invoice"), "{\"version\":" + f.version(id) + "}").with(asRep))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from invoice where order_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void invoicePdfOfOtherRepIs404() throws Exception {
        long id = confirmedOrder();
        long invoiceId = SalesFixture.id(issue(asRep, id));
        long other = f.user("oth", Role.SALES);
        mvc.perform(get("/api/invoices/" + invoiceId + ".pdf").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + id + "/invoices").with(as(other, Role.SALES)))
                .andExpect(status().isNotFound());
    }
}
