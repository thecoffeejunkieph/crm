package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.asContact;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class CreditMemoIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired org.springframework.core.env.Environment env;

    SalesFixture f;
    long rep;
    long customer;
    long product;
    long order; // 10 x 112.00 = 1120.00, fully collected, invoiced
    String invoiceNo;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() throws Exception {
        InvoicesIT.companySettings(jdbc, caches, "The Coffee Junkie Inc.", "123-456-789-000", "Makati City");
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        asAdmin = as(f.user("boss", Role.ADMIN), Role.ADMIN);
        customer = f.customer("acme", rep);
        product = f.product("A", "112.00", null, "VATABLE");
        order = f.createdOrder(as(rep, Role.SALES), customer, line(product, "10"));
        f.setStatus(order, "CONFIRMED");
        jdbc.update("update sales_order set settled_amount = total where id = ?", order);
        mvc.perform(f.json(post("/api/orders/" + order + "/invoice"), "{\"version\":" + f.version(order) + "}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(asAdmin)).andExpect(status().isOk());
        invoiceNo = jdbc.queryForObject("select number from invoice where order_id = ?", String.class, order);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from stock_adjustment where product_id = ?", product);
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where product_id = ?", product);
        f.cleanUp();
        InvoicesIT.companySettings(jdbc, caches, "", "", "");
    }

    private long approved(String amount) throws Exception {
        return SalesFixture.id(mvc.perform(f.json(post("/api/orders/" + order + "/refunds"),
                        "{\"kind\":\"MONEY_ONLY\",\"amount\":" + amount + ",\"reason\":\"Damaged in transit\"}")
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(asAdmin)).andExpect(status().isCreated()));
    }

    private long paid(String amount) throws Exception {
        long id = approved(amount);
        long version = jdbc.queryForObject("select version from refund where id = ?", Long.class, id);
        String json = "{\"method\":\"CASH\",\"paidOn\":\"" + java.time.LocalDate.now() + "\",\"version\":" + version + "}";
        mvc.perform(multipart("/api/refunds/" + id + "/payout")
                .file(new MockMultipartFile("payout", "", "application/json", json.getBytes()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(asAdmin))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void creditMemoShowsNumbersAndTotal() throws Exception {
        long id = paid("112.00");
        byte[] pdf = mvc.perform(get("/api/refunds/" + id + "/credit-memo.pdf").with(asAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains("CREDIT MEMO", "CM-", invoiceNo, "PHP 112.00", "VATable Sales", "PHP 100.00",
                "PHP 12.00", "Damaged in transit", "123-456-789-000");
    }

    @Test
    void portalContactCanDownloadOwnOnly() throws Exception {
        long id = paid("112.00");
        String url = "/api/portal/orders/" + order + "/refunds/" + id + "/credit-memo.pdf";
        mvc.perform(get(url).with(asContact(f.contact(customer, "cee", true), customer))).andExpect(status().isOk());
        long other = f.customer("other", rep);
        mvc.perform(get(url).with(asContact(f.contact(other, "oth", true), other))).andExpect(status().isNotFound());
    }

    @Test
    void unpaidRefundHasNoMemo() throws Exception {
        long id = approved("112.00");
        mvc.perform(get("/api/refunds/" + id + "/credit-memo.pdf").with(asAdmin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/portal/orders/" + order + "/refunds/" + id + "/credit-memo.pdf")
                .with(asContact(f.contact(customer, "cee", true), customer))).andExpect(status().isNotFound());
    }
}
