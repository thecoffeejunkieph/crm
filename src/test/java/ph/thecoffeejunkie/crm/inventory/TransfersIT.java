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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class TransfersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long rep;
    long src;
    long dst;
    long other;
    long p;
    long dstUserId;
    RequestPostProcessor admin;
    RequestPostProcessor srcUser;
    RequestPostProcessor dstUser;

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        src = f.warehouse("S");
        dst = f.warehouse("D");
        other = f.warehouse("O");
        p = f.product("P", "KG");
        admin = as(f.user("adm"), Role.ADMIN);
        srcUser = as(f.user("src"), Role.WAREHOUSE, src);
        dstUserId = f.user("dst");
        jdbc.update("update app_user set role = 'WAREHOUSE' where id = ?", dstUserId);
        jdbc.update("insert into user_warehouse (user_id, warehouse_id) values (?, ?)", dstUserId, dst);
        dstUser = as(dstUserId, Role.WAREHOUSE, dst);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from stock_adjustment where warehouse_id in (?, ?, ?)", src, dst, other);
        jdbc.update("delete from stock_transfer where from_warehouse_id in (?, ?, ?)", src, dst, other);
        jdbc.update("delete from user_warehouse where user_id = ?", dstUserId);
        f.cleanUp();
    }

    private long create(String qty, Long orderId) throws Exception {
        String body = "{\"fromWarehouseId\":" + src + ",\"toWarehouseId\":" + dst + ",\"salesOrderId\":" + orderId
                + ",\"notes\":\"n\",\"lines\":[{\"productId\":" + p + ",\"qty\":" + qty + "}]}";
        mvc.perform(post("/api/transfers").contentType("application/json").content(body).with(csrf(mvc)).with(admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value(startsWith("TR-")));
        return jdbc.queryForObject("select max(id) from stock_transfer where from_warehouse_id = ?", Long.class, src);
    }

    private ResultActions act(long id, String action, String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/transfers/" + id + "/" + action).contentType("application/json")
                .content(body == null ? "{}" : body).with(csrf(mvc)).with(who));
    }

    private long lineId(long id) {
        return jdbc.queryForObject("select id from stock_transfer_line where transfer_id = ?", Long.class, id);
    }

    private ResultActions receive(long id, String qty, RequestPostProcessor who) throws Exception {
        return act(id, "receive", "{\"lines\":[{\"lineId\":" + lineId(id) + ",\"receivedQty\":" + qty + "}]}", who);
    }

    @Test
    void dispatchMovesStockOut() throws Exception {
        f.receive(p, src, "10");
        long id = create("4", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        assertThat(f.onHand(p, src)).isEqualByComparingTo("6");
        assertThat(f.onHand(p, dst)).isEqualByComparingTo("0");
        // a second tap is refused and deducts nothing more
        act(id, "dispatch", null, srcUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(f.onHand(p, src)).isEqualByComparingTo("6");
    }

    @Test
    void dispatchInsufficient422() throws Exception {
        f.receive(p, src, "3");
        long id = create("4", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(f.onHand(p, src)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("select status from stock_transfer where id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void receiveLessPostsTransitLossNoMovement() throws Exception {
        f.receive(p, src, "10");
        long id = create("10", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isOk());
        receive(id, "8", dstUser).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.lines[0].receivedQty").value(8.0));
        assertThat(f.onHand(p, dst)).isEqualByComparingTo("8");
        assertThat(f.onHand(p, src)).isEqualByComparingTo("0");
        List<Map<String, Object>> srcLedger = jdbc.queryForList("select type, qty from stock_movement "
                + "where warehouse_id = ? and ref_type = 'TRANSFER'", src);
        assertThat(srcLedger).hasSize(1);
        assertThat(srcLedger.get(0).get("type")).isEqualTo("TRANSFER_OUT");
        assertThat((BigDecimal) srcLedger.get(0).get("qty")).isEqualByComparingTo("-10");
        Map<String, Object> adj = jdbc.queryForMap("select delta, source, status from stock_adjustment "
                + "where warehouse_id = ?", src);
        assertThat((BigDecimal) adj.get("delta")).isEqualByComparingTo("-2");
        assertThat(adj.get("source")).isEqualTo("TRANSIT_LOSS");
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where type = 'ADJUSTMENT' "
                + "and warehouse_id = ?", Integer.class, src)).isZero();
    }

    @Test
    void receiveMore400() throws Exception {
        f.receive(p, src, "10");
        long id = create("5", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isOk());
        receive(id, "6", dstUser).andExpect(status().isBadRequest());
        assertThat(f.onHand(p, dst)).isEqualByComparingTo("0");
        act(id, "receive", "{\"lines\":[]}", dstUser).andExpect(status().isBadRequest());
    }

    @Test
    void linkedOrderFilledFirstOnReceive() throws Exception {
        f.receive(p, src, "10");
        OrderStock older = f.order(rep, dst, Instant.now().minusSeconds(600), l(p, "5"));
        OrderStock linked = f.order(rep, dst, Instant.now(), l(p, "5"));
        tx.executeWithoutResult(s -> {
            inventory.reserve(older);
            inventory.reserve(linked);
        });
        long id = create("5", linked.orderId());
        act(id, "dispatch", null, srcUser).andExpect(status().isOk());
        receive(id, "5", dstUser).andExpect(status().isOk());
        assertThat(inventory.totalShort(linked.orderId())).isEqualByComparingTo("0");
        assertThat(inventory.totalShort(older.orderId())).isEqualByComparingTo("5");
    }

    @Test
    void cancelOnlyDraft() throws Exception {
        f.receive(p, src, "10");
        long a = create("2", null);
        act(a, "cancel", null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        act(a, "dispatch", null, srcUser).andExpect(status().isUnprocessableContent());
        long b = create("2", null);
        act(b, "dispatch", null, srcUser).andExpect(status().isOk());
        act(b, "cancel", null, admin).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void destinationOnlyUserCannotDispatch404() throws Exception {
        f.receive(p, src, "10");
        long id = create("2", null);
        act(id, "dispatch", null, dstUser).andExpect(status().isNotFound());
        assertThat(f.onHand(p, src)).isEqualByComparingTo("10");
    }

    @Test
    void destinationOnlyUserCannotCreate404() throws Exception {
        f.receive(p, src, "10");
        String body = "{\"fromWarehouseId\":" + src + ",\"toWarehouseId\":" + dst
                + ",\"lines\":[{\"productId\":" + p + ",\"qty\":2}]}";
        mvc.perform(post("/api/transfers").contentType("application/json").content(body).with(csrf(mvc)).with(dstUser))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from stock_transfer where from_warehouse_id = ?",
                Integer.class, src)).isZero();
        assertThat(f.onHand(p, src)).isEqualByComparingTo("10");
    }

    @Test
    void destinationOnlyUserCannotRepointUpdate404() throws Exception {
        long id = create("2", null);
        long v = jdbc.queryForObject("select version from stock_transfer where id = ?", Long.class, id);
        // dstUser has only dst: it cannot edit a transfer sourced from src, nor repoint one to source from dst
        for (long from : new long[] {src, dst}) {
            String body = "{\"fromWarehouseId\":" + from + ",\"toWarehouseId\":" + other + ",\"version\":" + v
                    + ",\"lines\":[{\"productId\":" + p + ",\"qty\":9}]}";
            mvc.perform(put("/api/transfers/" + id).contentType("application/json").content(body)
                    .with(csrf(mvc)).with(dstUser)).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("select from_warehouse_id from stock_transfer where id = ?", Long.class, id))
                .isEqualTo(src);
        assertThat(jdbc.queryForObject("select qty from stock_transfer_line where transfer_id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("2");
    }

    @Test
    void sourceOnlyUserCannotReceive404() throws Exception {
        f.receive(p, src, "10");
        long id = create("2", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isOk());
        receive(id, "2", srcUser).andExpect(status().isNotFound());
        assertThat(f.onHand(p, dst)).isEqualByComparingTo("0");
    }

    @Test
    void destinationUsersNotifiedOnDispatch() throws Exception {
        f.receive(p, src, "10");
        long id = create("2", null);
        act(id, "dispatch", null, srcUser).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? "
                + "and type = 'TRANSFER_INBOUND'", Integer.class, dstUserId)).isEqualTo(1);
    }

    @Test
    void listAndGetScopedToAssignedEnds() throws Exception {
        long id = create("2", null);
        mvc.perform(get("/api/transfers").with(dstUser)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/transfers/" + id).with(as(f.user("w3"), Role.WAREHOUSE, other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transfers/" + id).with(as(rep, Role.SALES))).andExpect(status().isOk());
    }

    @Test
    void pdfShowsTheTransferAndIsScoped() throws Exception {
        long id = create("2", null);
        byte[] pdf = mvc.perform(get("/api/transfers/" + id + ".pdf").with(dstUser)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(ph.thecoffeejunkie.crm.sales.PdfText.of(pdf)).contains("STOCK TRANSFER");
        mvc.perform(get("/api/transfers/" + id + ".pdf").with(as(f.user("w3"), Role.WAREHOUSE, other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByNumber() throws Exception {
        long first = create("2", null);
        long second = create("2", null);
        String number = jdbc.queryForObject("select number from stock_transfer where id = ?", String.class, first);
        mvc.perform(get("/api/transfers").param("q", number.toLowerCase()).with(dstUser)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + first + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == " + second + ")]").isEmpty());
    }

    @Test
    void coverShortageCreatesLinkedDraftPerSource() {
        f.receive(p, src, "3");
        f.receive(p, other, "20");
        OrderStock o = f.order(rep, dst, Instant.now(), l(p, "8"));
        tx.executeWithoutResult(s -> inventory.reserve(o));
        var out = tx.execute(s -> inventory.coverShortage(o.orderId(), rep));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).fromWarehouseId()).isEqualTo(other);
        assertThat(out.get(0).toWarehouseId()).isEqualTo(dst);
        assertThat(out.get(0).salesOrderId()).isEqualTo(o.orderId());
        assertThat(out.get(0).lines().get(0).qty()).isEqualByComparingTo("8");
    }
}
