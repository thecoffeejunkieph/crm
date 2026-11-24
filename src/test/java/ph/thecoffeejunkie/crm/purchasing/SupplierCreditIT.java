package ph.thecoffeejunkie.crm.purchasing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class SupplierCreditIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired CreditQuery credit;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long product;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        wh = f.inv.warehouse("A");
        product = f.inv.product("P1", "PC");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private long po(long supplier, String status, String qty, String cost, String received, boolean closedShort) {
        long id = jdbc.queryForObject("insert into purchase_order (number, supplier_id, warehouse_id, status, closed_short) "
                + "values (?, ?, ?, ?, ?) returning id", Long.class, f.code("PO" + System.nanoTime()), supplier, wh, status,
                closedShort);
        jdbc.update("insert into purchase_order_line (po_id, product_id, qty, unit_cost, received_qty) values (?, ?, ?, ?, ?)",
                id, product, new BigDecimal(qty), new BigDecimal(cost), new BigDecimal(received));
        return id;
    }

    private long supplier(String code, String limit, String opening) {
        long id = f.supplier(code, limit == null ? null : new BigDecimal(limit));
        jdbc.update("update supplier set opening_balance = ? where id = ?", new BigDecimal(opening), id);
        return id;
    }

    private SupplierCredit credit(long id) {
        return credit.forSuppliers(List.of(id)).get(id);
    }

    @Test
    void balanceIsOpeningPlusReceivedMinusPaid() throws Exception {
        long s = supplier("S1", "5000", "1000");
        long po = po(s, "PARTIALLY_RECEIVED", "10", "12.50", "4", false);
        receipt(po, "2026-11-19", "4");
        mvc.perform(post("/api/suppliers/" + s + "/payments").contentType("application/json")
                .content("{\"amount\":30,\"paidOn\":\"2026-11-20\"}").with(csrf(mvc)).with(admin))
                .andExpect(status().isCreated());
        assertThat(credit(s).balance()).isEqualByComparingTo("1020.00");
        mvc.perform(get("/api/suppliers/" + s).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.credit.balance").value(1020.00))
                .andExpect(jsonPath("$.credit.committed").value(75.00))
                .andExpect(jsonPath("$.credit.available").value(3905.00));
    }

    @Test
    void cancelledPoDoesNotCountTowardBalance() {
        long s = supplier("S2", null, "0");
        po(s, "CANCELLED", "10", "1.00", "3", false);
        assertThat(credit(s).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void committedCountsRemainingOnOpenPosOnly() {
        long s = supplier("S3", "10000", "0");
        po(s, "DRAFT", "10", "10.00", "0", false);
        po(s, "CANCELLED", "10", "10.00", "0", false);
        po(s, "RECEIVED", "10", "10.00", "6", true); // closed short
        po(s, "PENDING_APPROVAL", "2", "10.00", "0", false); // 20
        po(s, "SENT", "3", "10.00", "0", false); // 30
        po(s, "PARTIALLY_RECEIVED", "5", "10.00", "2", false); // 30
        assertThat(credit(s).committed()).isEqualByComparingTo("80.00");
    }

    @Test
    void noLimitMeansNullAvailableAndFlag() {
        long s = supplier("S4", null, "500");
        SupplierCredit c = credit(s);
        assertThat(c.limit()).isNull();
        assertThat(c.available()).isNull();
        assertThat(c.flag()).isNull();
        assertThat(c.balance()).isEqualByComparingTo("500.00");
    }

    @Test
    void nearAtNinetyPercentOverBelowZero() {
        long near = supplier("S5", "1000", "900");
        long ok = supplier("S6", "1000", "899.99");
        long over = supplier("S7", "1000", "1000.01");
        long exact = supplier("S8", "1000", "1000");
        assertThat(credit(near).flag()).isEqualTo("NEAR");
        assertThat(credit(ok).flag()).isNull();
        assertThat(credit(over).flag()).isEqualTo("OVER");
        assertThat(credit(over).available()).isEqualByComparingTo("-0.01");
        assertThat(credit(exact).flag()).isEqualTo("NEAR"); // available 0 is not over
        assertThat(credit.forSuppliers(List.of(near, ok, over)).keySet()).containsExactlyInAnyOrder(near, ok, over);
    }

    @Test
    void idempotentRetryRecordsOnePayment() throws Exception {
        long s = supplier("S1", "5000", "1000");
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/suppliers/" + s + "/payments").contentType("application/json")
                    .content("{\"amount\":30,\"paidOn\":\"2026-11-20\"}").header("Idempotency-Key", key)
                    .with(csrf(mvc)).with(admin)).andExpect(status().isCreated())
                    .andExpect(header().string("Idempotent-Replayed", i == 0 ? null : "true"));
        }
        assertThat(jdbc.queryForObject("select count(*) from supplier_payment where supplier_id = ?", Integer.class, s))
                .isEqualTo(1);
        assertThat(credit(s).balance()).isEqualByComparingTo("970.00");
    }

    @Test
    void paymentDeleteIsAudited() throws Exception {
        long s = supplier("S9", null, "100");
        String res = mvc.perform(post("/api/suppliers/" + s + "/payments").contentType("application/json")
                .content("{\"amount\":40,\"paidOn\":\"2026-11-20\",\"method\":\"BANK\"}").with(csrf(mvc)).with(admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long pid = ((Number) JsonPath.read(res, "$.id")).longValue();
        mvc.perform(get("/api/suppliers/" + s + "/payments").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(40.00));
        mvc.perform(post("/api/suppliers/" + s + "/payments").contentType("application/json")
                .content("{\"amount\":0,\"paidOn\":\"2026-11-20\"}").with(csrf(mvc)).with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/suppliers/" + s + "/payments/" + pid).with(csrf(mvc)).with(admin))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/suppliers/" + s + "/payments/" + pid).with(csrf(mvc)).with(admin))
                .andExpect(status().isNotFound());
        assertThat(credit(s).balance()).isEqualByComparingTo("100.00");
        List<String> actions = jdbc.queryForList("select action from audit_event where entity_type = 'SUPPLIER_PAYMENT' "
                + "and entity_id = ? order by id", String.class, pid);
        assertThat(actions).containsExactly("PAYMENT_RECORDED", "PAYMENT_DELETED");
    }

    @Test
    void oversizedPaymentInputIs400() throws Exception {
        long s = supplier("S11", null, "0");
        for (String json : List.of("{\"amount\":1.234,\"paidOn\":\"2026-11-20\"}",
                "{\"amount\":10000000000000,\"paidOn\":\"2026-11-20\"}",
                "{\"amount\":1,\"paidOn\":\"2026-11-20\",\"method\":\"" + "x".repeat(51) + "\"}",
                "{\"amount\":1,\"paidOn\":\"2026-11-20\",\"reference\":\"" + "x".repeat(101) + "\"}",
                "{\"amount\":1,\"paidOn\":\"2026-11-20\",\"notes\":\"" + "x".repeat(1001) + "\"}")) {
            mvc.perform(post("/api/suppliers/" + s + "/payments").contentType("application/json").content(json)
                    .with(csrf(mvc)).with(admin)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void statementRunningBalance() throws Exception {
        long s = supplier("S10", null, "1000");
        long po = po(s, "RECEIVED", "10", "12.50", "10", false);
        receipt(po, "2026-10-01", "4"); // 50, before the window
        receipt(po, "2026-11-05", "6"); // 75
        jdbc.update("insert into supplier_payment (supplier_id, amount, paid_on, reference) values (?, 30, '2026-11-10', 'CHQ-1')", s);
        jdbc.update("insert into supplier_payment (supplier_id, amount, paid_on) values (?, 500, '2026-09-01')", s);
        // opening at 2026-11-01 = 1000 - 500 + 50 = 550; +75 = 625; -30 = 595
        mvc.perform(get("/api/suppliers/" + s + "/statement").param("from", "2026-11-01").param("to", "2026-11-30").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opening").value(550.00))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].kind").value("RECEIPT"))
                .andExpect(jsonPath("$.entries[0].amount").value(75.00))
                .andExpect(jsonPath("$.entries[0].balance").value(625.00))
                .andExpect(jsonPath("$.entries[1].kind").value("PAYMENT"))
                .andExpect(jsonPath("$.entries[1].reference").value("CHQ-1"))
                .andExpect(jsonPath("$.entries[1].amount").value(-30.00))
                .andExpect(jsonPath("$.entries[1].balance").value(595.00))
                .andExpect(jsonPath("$.closing").value(595.00));
    }

    @Test
    void balanceMatchesStatementClosingWithFractionalPartialReceipts() throws Exception {
        long s = supplier("S12", null, "0");
        long kg = f.inv.product("KG1", "KG");
        long id = jdbc.queryForObject("insert into purchase_order (number, supplier_id, warehouse_id, status, closed_short) "
                + "values (?, ?, ?, 'RECEIVED', false) returning id", Long.class, f.code("PO" + System.nanoTime()), s, wh);
        jdbc.update("insert into purchase_order_line (po_id, product_id, qty, unit_cost, received_qty) values (?, ?, 1, 33.33, 1)",
                id, kg);
        receipt(id, kg, "2026-11-01", "0.333"); // 11.09889 -> 11.10
        receipt(id, kg, "2026-11-02", "0.667"); // 22.23111 -> 22.23
        // Per receipt line: 11.10 + 22.23 = 33.33 (rounding the cumulative 1 x 33.33 would also give 33.33)
        String res = mvc.perform(get("/api/suppliers/" + s + "/statement").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.closing").value(33.33)).andReturn().getResponse().getContentAsString();
        BigDecimal closing = new BigDecimal(JsonPath.read(res, "$.closing").toString());
        assertThat(credit(s).balance()).isEqualByComparingTo(closing);
        // Second check where per-line and cumulative rounding differ: 0.05 x (0.1 + 0.1)
        long s2 = supplier("S13", null, "0");
        long id2 = jdbc.queryForObject("insert into purchase_order (number, supplier_id, warehouse_id, status, closed_short) "
                + "values (?, ?, ?, 'PARTIALLY_RECEIVED', false) returning id", Long.class, f.code("PO" + System.nanoTime()), s2, wh);
        jdbc.update("insert into purchase_order_line (po_id, product_id, qty, unit_cost, received_qty) values (?, ?, 1, 0.05, 0.2)",
                id2, kg);
        receipt(id2, kg, "2026-11-01", "0.1");
        receipt(id2, kg, "2026-11-02", "0.1");
        String res2 = mvc.perform(get("/api/suppliers/" + s2 + "/statement").with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.closing").value(0.02)).andReturn().getResponse().getContentAsString();
        assertThat(credit(s2).balance()).isEqualByComparingTo(new BigDecimal(JsonPath.read(res2, "$.closing").toString()));
    }

    private void receipt(long poId, String date, String qty) {
        receipt(poId, product, date, qty);
    }

    private void receipt(long poId, long productId, String date, String qty) {
        long r = jdbc.queryForObject("insert into stock_receipt (number, warehouse_id, received_on, purchase_order_id) "
                + "values (?, ?, ?::date, ?) returning id", Long.class, f.code("R" + System.nanoTime()), wh, date, poId);
        jdbc.update("insert into stock_receipt_line (receipt_id, product_id, qty) values (?, ?, ?)", r, productId, new BigDecimal(qty));
    }
}
