package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;

/** POST /api/pos/sales: one transaction from cart to a fulfilled, paid, invoiced order. Mail-capable for the receipt. */
@MailIntegrationTest
class PosCheckoutIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired Environment env;
    @Autowired JavaMailSender mail;
    @Autowired @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor;

    SalesFixture f;
    long rep;
    long customer;
    long a;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        MailSupport.stub(mail);
        InvoicesIT.companySettings(jdbc, caches, "The Coffee Junkie Inc.", "123-456-789-000", "Makati City");
        f = new SalesFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        customer = f.customer("acme", rep);
        a = f.product("A", "112.00", null, "VATABLE");
        long wh = f.defaultWarehouse();
        f.stock(a, wh, "10");
        asRep = as(rep, Role.SALES);
        setting("approval.discountPercentMax", "15");
        setting("pos.warehouseId", String.valueOf(wh));
    }

    @AfterEach
    void cleanUp() {
        // The ledger is append-only for crm_app; a sale posts DISPATCH movements, removed here as the owner.
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where product_id in (select id from product where sku like ?)",
                        f.tag + "%");
        f.cleanUp();
        InvoicesIT.companySettings(jdbc, caches, "", "", "");
        setting("pos.warehouseId", "0");
    }

    private void setting(String key, String value) {
        jdbc.update("update app_setting set value = to_jsonb(?::numeric) where key = ?", value, key);
        caches.getCache("settings").clear();
    }

    private static String line(long productId, String qty, String unitPrice, String discountPct) {
        return "{\"productId\":" + productId + ",\"qty\":" + qty + ",\"unitPrice\":" + unitPrice
                + (discountPct == null ? "" : ",\"discountPct\":" + discountPct) + "}";
    }

    private static String pay(String method, String amount, String tendered) {
        return "{\"method\":\"" + method + "\",\"amount\":" + amount
                + (tendered == null ? "" : ",\"tendered\":" + tendered) + "}";
    }

    private ResultActions checkout(long customerId, List<String> lines, List<String> payments, boolean email,
                                   String key) throws Exception {
        String body = "{\"customerId\":" + customerId + ",\"lines\":[" + String.join(",", lines) + "],\"payments\":["
                + String.join(",", payments) + "],\"emailInvoice\":" + email + "}";
        return mvc.perform(f.json(post("/api/pos/sales"), body).header("Idempotency-Key", key).with(asRep));
    }

    private ResultActions checkout(List<String> lines, List<String> payments) throws Exception {
        return checkout(customer, lines, payments, false, UUID.randomUUID().toString());
    }

    private ResultActions sellTwo() throws Exception {
        return checkout(List.of(line(a, "2", "112.00", null)), List.of(pay("CASH", "224.00", null)));
    }

    private static BigDecimal num(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    @Test
    void splitTenderSaleCompletes() throws Exception {
        String body = SalesFixture.body(checkout(List.of(line(a, "2", "112.00", null)),
                List.of(pay("CASH", "100.00", "200.00"), pay("GCASH", "124.00", null)))
                .andExpect(status().isCreated()));
        assertThat(num(body, "$.changeDue")).isEqualByComparingTo("100.00");
        assertThat(num(body, "$.total")).isEqualByComparingTo("224.00");
        long orderId = ((Number) JsonPath.read(body, "$.orderId")).longValue();
        long invoiceId = ((Number) JsonPath.read(body, "$.invoiceId")).longValue();
        String invoiceNumber = JsonPath.read(body, "$.invoiceNumber");
        assertThat(invoiceNumber).startsWith("SI-");
        assertThat((String) JsonPath.read(body, "$.orderNumber")).startsWith("SO-");

        Map<String, Object> o = jdbc.queryForMap("select status, channel, total - settled_amount balance, "
                + "delivery_method, released_at, fulfilled_at from sales_order where id = ?", orderId);
        assertThat(o).containsEntry("status", "FULFILLED").containsEntry("channel", "POS")
                .containsEntry("delivery_method", "PICKUP");
        assertThat((BigDecimal) o.get("balance")).isEqualByComparingTo("0");
        assertThat(o.get("released_at")).isNotNull();
        assertThat(o.get("fulfilled_at")).isNotNull();

        assertThat(jdbc.queryForList("select number from invoice where order_id = ? and status = 'ISSUED'",
                String.class, orderId)).containsExactly(invoiceNumber);
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id = ? and status = 'VERIFIED'",
                Integer.class, orderId)).isEqualTo(2);
        Map<String, Object> m = jdbc.queryForMap("select qty, ref_type, ref_id, reason from stock_movement "
                + "where product_id = ? and type = 'DISPATCH'", a);
        assertThat((BigDecimal) m.get("qty")).isEqualByComparingTo("-2");
        assertThat(m).containsEntry("reason", invoiceNumber).containsEntry("ref_type", "INVOICE")
                .containsEntry("ref_id", invoiceId);
        assertThat(jdbc.queryForObject("select count(*) from delivery_order where sales_order_id = ?", Integer.class,
                orderId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from stock_reservation where sales_order_id = ?", Integer.class,
                orderId)).isZero();
        assertThat(jdbc.queryForObject("select on_hand from stock_level where product_id = ?", BigDecimal.class, a))
                .isEqualByComparingTo("8");
    }

    @Test
    void priceBelowFloorRejected() throws Exception {
        String body = SalesFixture.body(checkout(List.of(line(a, "1", "80.00", null)), List.of(pay("CASH", "80.00", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_PRICE_NOT_ALLOWED")));
        assertThat(((Number) JsonPath.read(body, "$.details[0].productId")).longValue()).isEqualTo(a);
        assertThat(num(body, "$.details[0].minUnitPrice")).isEqualByComparingTo("95.20");
        assertThat((List<?>) JsonPath.read(body, "$.details")).hasSize(1);
    }

    @Test
    void discountOverCapRejected() throws Exception {
        checkout(List.of(line(a, "1", "200.00", "20")), List.of(pay("CASH", "160.00", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_PRICE_NOT_ALLOWED"));
    }

    @Test
    void netRoundingUpToFloorAccepted() throws Exception {
        // 95.30 less 0.11% = 95.19517: below the 95.20 floor unrounded, exactly on it at 2 places (HALF_UP).
        checkout(List.of(line(a, "1", "95.30", "0.11")), List.of(pay("CASH", "95.20", null)))
                .andExpect(status().isCreated());
        // 95.30 less 0.12% = 95.18564 -> 95.19, below it.
        checkout(List.of(line(a, "1", "95.30", "0.12")), List.of(pay("CASH", "95.19", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_PRICE_NOT_ALLOWED"));
    }

    @Test
    void subCentBelowCostRejectedAsPriceNotApproval() throws Exception {
        long b = f.product("B", "112.00", "100.00", "VATABLE");
        f.stock(b, f.defaultWarehouse(), "5");
        // 100.10 less 0.1% = 99.9999: 100.00 at 2 places, but under cost unrounded, which is what BELOW_COST checks.
        checkout(List.of(line(b, "1", "100.10", "0.1")), List.of(pay("CASH", "100.00", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_PRICE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details[0].minUnitPrice").value(100.00));
        assertThat(orderCount()).isZero();
    }

    @Test
    void discountWithThreeDecimalsRejected() throws Exception {
        checkout(List.of(line(a, "1", "112.00", "12.345")), List.of(pay("CASH", "98.17", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].discountPct"));
        checkout(List.of(line(a, "1", "112.00", null)), List.of(pay("CASH", "112.00", "200.001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("payments[0].tendered"));
        assertThat(orderCount()).isZero();
    }

    @Test
    void nonRegularTaxCustomerRejected() throws Exception {
        jdbc.update("update customer set tax_treatment = 'VAT_EXEMPT' where id = ?", customer);
        List<Long> before = counts();
        long counter = invoiceCounter();
        sellTwo().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_TAX_TREATMENT_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.detail")
                        .value("This customer is VAT-exempt or zero-rated. Sell as a regular order."));
        assertThat(counts()).isEqualTo(before);
        assertThat(invoiceCounter()).isEqualTo(counter);
    }

    @Test
    void failureAfterInvoiceIssueRollsBackCounter() throws Exception {
        // Test-only trigger: the DISPATCH insert in fulfilAtCounter (after the invoice is issued) fails for this product.
        JdbcTemplate owner = new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")));
        String fn = "pos_it_fail_" + Math.abs(f.tag.hashCode());
        owner.execute("create function " + fn + "() returns trigger language plpgsql as $$ begin "
                + "raise exception 'forced failure'; end $$");
        owner.execute("create trigger " + fn + " before insert on stock_movement for each row "
                + "when (new.product_id = " + a + ") execute function " + fn + "()");
        try {
            List<Long> before = counts();
            long counter = invoiceCounter();
            int status = sellTwo().andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(500);
            assertThat(counts()).isEqualTo(before);
            assertThat(invoiceCounter()).isEqualTo(counter);
            assertThat(f.reserved(a, f.defaultWarehouse())).isEqualByComparingTo("0");
        } finally {
            owner.execute("drop trigger " + fn + " on stock_movement");
            owner.execute("drop function " + fn + "()");
        }
    }

    @Test
    void overdueCustomerCanBuyAtCounter() throws Exception {
        long overdue = f.createdOrder(asRep, customer, SalesFixture.line(a, "1"));
        jdbc.update("update sales_order set status = 'CONFIRMED', due_date = current_date - 5 where id = ?", overdue);
        sellTwo().andExpect(status().isCreated());
    }

    @Test
    void paymentMismatchRejected() throws Exception {
        String body = SalesFixture.body(checkout(List.of(line(a, "2", "112.00", null)),
                List.of(pay("CASH", "200.00", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("POS_PAYMENT_MISMATCH")));
        assertThat(num(body, "$.total")).isEqualByComparingTo("224.00");
        assertThat(num(body, "$.paid")).isEqualByComparingTo("200.00");
        assertThat(orderCount()).isZero();
    }

    @Test
    void outOfStockRollsBackEverything() throws Exception {
        List<Long> before = counts();
        long counter = invoiceCounter();
        String body = SalesFixture.body(checkout(List.of(line(a, "11", "112.00", null)),
                List.of(pay("CASH", "1232.00", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POS_OUT_OF_STOCK")));
        assertThat(((Number) JsonPath.read(body, "$.details[0].productId")).longValue()).isEqualTo(a);
        assertThat(num(body, "$.details[0].requested")).isEqualByComparingTo("11");
        assertThat(num(body, "$.details[0].available")).isEqualByComparingTo("10");
        assertThat(counts()).isEqualTo(before);
        assertThat(invoiceCounter()).isEqualTo(counter);
        assertThat(f.reserved(a, f.defaultWarehouse())).isEqualByComparingTo("0");
    }

    @Test
    void tenderedBelowAmountRejected() throws Exception {
        checkout(List.of(line(a, "2", "112.00", null)), List.of(pay("CASH", "224.00", "100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("payments[0].tendered"));
        checkout(List.of(line(a, "2", "112.00", null)), List.of(pay("GCASH", "224.00", "224.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("payments[0].tendered"));
        assertThat(orderCount()).isZero();
    }

    @Test
    void duplicateProductRejected() throws Exception {
        checkout(List.of(line(a, "1", "112.00", null), line(a, "1", "112.00", null)),
                List.of(pay("CASH", "224.00", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"));
    }

    @Test
    void archivedCustomerRejected() throws Exception {
        jdbc.update("update customer set archived_at = now() where id = ?", customer);
        sellTwo().andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("customerId"));
        assertThat(orderCount()).isZero();
    }

    @Test
    void retryWithSameKeyReturnsSameSale() throws Exception {
        String key = UUID.randomUUID().toString();
        List<String> lines = List.of(line(a, "2", "112.00", null));
        List<String> payments = List.of(pay("CASH", "224.00", null));
        String first = SalesFixture.body(checkout(customer, lines, payments, false, key).andExpect(status().isCreated()));
        String second = SalesFixture.body(checkout(customer, lines, payments, false, key).andExpect(status().isCreated()));
        assertThat((String) JsonPath.read(second, "$.invoiceNumber"))
                .isEqualTo(JsonPath.read(first, "$.invoiceNumber"));
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void emailsInvoiceWhenAsked() throws Exception {
        jdbc.update("update customer set email = ? where id = ?", f.tag.toLowerCase() + "@buyer.ph", customer);
        List<String> lines = List.of(line(a, "1", "112.00", null));
        List<String> payments = List.of(pay("CASH", "112.00", null));
        String body = SalesFixture.body(checkout(customer, lines, payments, true, UUID.randomUUID().toString())
                .andExpect(status().isCreated()));
        List<MimeMessage> sent = MailSupport.awaitMail(mail, 1);
        assertThat(attachments(sent.get(0))).containsExactly(JsonPath.read(body, "$.invoiceNumber") + ".pdf");
        assertThat(sent.get(0).getAllRecipients()[0].toString()).isEqualTo(f.tag.toLowerCase() + "@buyer.ph");

        checkout(customer, lines, payments, false, UUID.randomUUID().toString()).andExpect(status().isCreated());
        MailSupport.awaitIdle(executor);
        assertThat(MailSupport.sent(mail)).hasSize(1);
    }

    // --- helpers ---

    private int orderCount() {
        return jdbc.queryForObject("select count(*) from sales_order where customer_id = ?", Integer.class, customer);
    }

    private long invoiceCounter() {
        return jdbc.queryForObject("select next_value from document_counter where name = 'INVOICE'", Long.class);
    }

    private List<Long> counts() {
        String orders = "(select id from sales_order where customer_id = " + customer + ")";
        return List.of(
                jdbc.queryForObject("select count(*) from sales_order where customer_id = ?", Long.class, customer),
                jdbc.queryForObject("select count(*) from invoice where order_id in " + orders, Long.class),
                jdbc.queryForObject("select count(*) from payment where order_id in " + orders, Long.class),
                jdbc.queryForObject("select count(*) from stock_movement where product_id = ?", Long.class, a),
                jdbc.queryForObject("select count(*) from stock_reservation where product_id = ?", Long.class, a));
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
}
