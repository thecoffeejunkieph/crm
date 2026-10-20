package ph.thecoffeejunkie.crm.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class AdjustmentsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long rep;
    long wh1;
    long p;
    RequestPostProcessor admin;
    RequestPostProcessor warehouse1;

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        wh1 = f.warehouse("A");
        p = f.product("P", "KG");
        jdbc.update("update product set cost = 100 where id = ?", p);
        admin = as(f.user("adm"), Role.ADMIN);
        warehouse1 = as(f.user("wh"), Role.WAREHOUSE, wh1);
    }

    @AfterEach
    void cleanUp() {
        List<Long> adjustments = jdbc.queryForList("select id from stock_adjustment where warehouse_id = ?",
                Long.class, wh1);
        jdbc.update("delete from stock_adjustment where warehouse_id = ?", wh1);
        adjustments.forEach(id -> jdbc.update("delete from approval_request where subject_type = 'STOCK_ADJUSTMENT' "
                + "and subject_id = ?", id));
        f.cleanUp();
    }

    private ResultActions adjust(String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/stock/adjustments").contentType("application/json").content(body)
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(who));
    }

    private ResultActions counted(String qty, RequestPostProcessor who) throws Exception {
        return adjust("{\"warehouseId\":" + wh1 + ",\"productId\":" + p + ",\"mode\":\"COUNTED\",\"countedQty\":" + qty
                + ",\"reason\":\"count\"}", who);
    }

    private ResultActions delta(String qty, RequestPostProcessor who) throws Exception {
        return adjust("{\"warehouseId\":" + wh1 + ",\"productId\":" + p + ",\"mode\":\"DELTA\",\"delta\":" + qty
                + ",\"reason\":\"damaged\"}", who);
    }

    private long adjustmentId() {
        return jdbc.queryForObject("select id from stock_adjustment where warehouse_id = ?", Long.class, wh1);
    }

    private void decide(boolean approve) throws Exception {
        long approval = jdbc.queryForObject("select approval_request_id from stock_adjustment where id = ?",
                Long.class, adjustmentId());
        mvc.perform(post("/api/approvals/" + approval + "/decision").contentType("application/json")
                .content("{\"approve\":" + approve + "}").with(csrf(mvc)).with(admin)).andExpect(status().isOk());
    }

    private String adjStatus() {
        return jdbc.queryForObject("select status from stock_adjustment where id = ?", String.class, adjustmentId());
    }

    @Test
    void countedResolvesDelta() throws Exception {
        f.receive(p, wh1, "10");
        counted("7.5", warehouse1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED")).andExpect(jsonPath("$.delta").value(-2.5))
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("ADJ-")));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("7.5");
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where ref_type = 'STOCK_ADJUSTMENT' "
                + "and ref_id = ? and type = 'ADJUSTMENT'", Integer.class, adjustmentId())).isEqualTo(1);
    }

    @Test
    void increasePostsImmediately() throws Exception {
        f.receive(p, wh1, "1");
        delta("500", admin).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("POSTED"));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("501");
    }

    @Test
    void smallDecreasePosts() throws Exception {
        f.receive(p, wh1, "100");
        delta("-50", warehouse1).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("POSTED"));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("50");
    }

    @Test
    void largeDecreasePendsThenPostsOnApproval() throws Exception {
        f.receive(p, wh1, "100");
        delta("-60", warehouse1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from approval_request where type = 'STOCK_WRITE_OFF' "
                + "and subject_id = ? and status = 'PENDING'", Integer.class, adjustmentId())).isEqualTo(1);
        decide(true);
        assertThat(adjStatus()).isEqualTo("POSTED");
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("40");
    }

    @Test
    void countedRecomputedAtApproval() throws Exception {
        f.receive(p, wh1, "100");
        counted("30", warehouse1).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.delta").value(-70.0));
        f.receive(p, wh1, "10");
        decide(true);
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("select delta from stock_adjustment where id = ?",
                BigDecimal.class, adjustmentId())).isEqualByComparingTo("-80");
    }

    @Test
    void rejectedLeavesStock() throws Exception {
        f.receive(p, wh1, "100");
        delta("-60", warehouse1).andExpect(status().isCreated());
        decide(false);
        assertThat(adjStatus()).isEqualTo("REJECTED");
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("100");
    }

    @Test
    void decreaseBelowReservedTakesYoungest() throws Exception {
        long rep2 = f.user("rep2");
        f.receive(p, wh1, "10");
        OrderStock older = f.order(rep, wh1, Instant.now().minusSeconds(100), l(p, "4"));
        OrderStock younger = f.order(rep2, wh1, Instant.now().minusSeconds(10), l(p, "5"));
        inventory.reserve(older);
        inventory.reserve(younger);
        assertThat(f.reserved(p, wh1)).isEqualByComparingTo("9");

        delta("-3", warehouse1).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("POSTED"));

        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("7");
        assertThat(f.reserved(p, wh1)).isEqualByComparingTo("7");
        assertThat(inventory.totalShort(younger.orderId())).isEqualByComparingTo("2");
        assertThat(inventory.totalShort(older.orderId())).isEqualByComparingTo("0");
        List<String> titles = jdbc.queryForList("select title from notification where recipient_user_id = ? "
                + "and type = 'RESERVATION_TAKEN'", String.class, rep2);
        assertThat(titles).containsExactly("Stock count took 2 kg of " + f.tag + "P from order "
                + younger.orderNumber());
    }

    @Test
    void pendingResponseOmitsCostForWarehouse() throws Exception {
        f.receive(p, wh1, "100");
        String body = delta("-60", warehouse1).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("cost").doesNotContain("value").doesNotContain("6000");
    }

    @Test
    void reasonRequired() throws Exception {
        adjust("{\"warehouseId\":" + wh1 + ",\"productId\":" + p + ",\"mode\":\"DELTA\",\"delta\":1,\"reason\":\" \"}",
                admin).andExpect(status().isBadRequest());
        adjust("{\"warehouseId\":" + wh1 + ",\"productId\":" + p + ",\"mode\":\"COUNTED\",\"reason\":\"x\"}", admin)
                .andExpect(status().isBadRequest());
        adjust("{\"warehouseId\":" + wh1 + ",\"productId\":" + p + ",\"mode\":\"DELTA\",\"delta\":0,\"reason\":\"x\"}",
                admin).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from stock_adjustment where warehouse_id = ?", Integer.class,
                wh1)).isZero();
    }

    @Test
    void listIsScoped() throws Exception {
        f.receive(p, wh1, "10");
        delta("1", admin).andExpect(status().isCreated());
        mvc.perform(get("/api/stock/adjustments").param("warehouseId", "" + wh1).param("status", "POSTED")
                .with(warehouse1)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].delta").value(1.0));
    }

    @Test
    void systemAdjustmentComposesWithShortPick() {
        f.receive(p, wh1, "10");
        OrderStock o = f.order(rep, wh1, Instant.now(), l(p, "10"));
        inventory.reserve(o);
        // Short pick of 2: the caller first lowers the reservation, then posts the adjustment.
        tx.executeWithoutResult(s -> {
            StockLevel level = ledger.lock(p, wh1);
            ledger.changeReserved(level, new BigDecimal("-2"));
            inventory.postSystemAdjustment(wh1, p, new BigDecimal("-2"), AdjustmentSource.SHORT_PICK, "short",
                    "DELIVERY_ORDER", 1);
        });
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("8");
        assertThat(f.reserved(p, wh1)).isEqualByComparingTo("8");
        tx.executeWithoutResult(s -> inventory.postSystemAdjustment(wh1, p, new BigDecimal("-1"),
                AdjustmentSource.TRANSIT_LOSS, "lost", "TRANSFER", 1));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("select count(*) from stock_adjustment where warehouse_id = ? "
                + "and status = 'POSTED'", Integer.class, wh1)).isEqualTo(2);
    }
}
