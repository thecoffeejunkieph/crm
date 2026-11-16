package ph.thecoffeejunkie.crm.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryRequest;
import ph.thecoffeejunkie.crm.fulfilment.Fulfilment;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

/** Spec section 9: after any mix of operations, every level equals its ledger and its reservations. */
@IntegrationTest
class LedgerConsistencyIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;
    @Autowired Fulfilment fulfilment;
    @Autowired Environment env;

    InventoryFixture f;
    long rep;
    long src;
    long dst;
    long p;
    RequestPostProcessor admin;
    RequestPostProcessor srcUser;
    RequestPostProcessor dstUser;

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        src = f.warehouse("S");
        dst = f.warehouse("D");
        p = f.product("P", "KG");
        admin = as(f.user("adm"), Role.ADMIN);
        srcUser = as(f.user("src"), Role.WAREHOUSE, src);
        dstUser = as(f.user("dst"), Role.WAREHOUSE, dst);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from delivery_order where warehouse_id in (?, ?)", src, dst);
        jdbc.update("delete from stock_adjustment where warehouse_id in (?, ?)", src, dst);
        jdbc.update("delete from stock_transfer where from_warehouse_id in (?, ?)", src, dst);
        // The ledger is append-only for crm_app: clear it as the owner so the fixture can delete its users.
        new JdbcTemplate(new DriverManagerDataSource(env.getProperty("spring.datasource.url"),
                env.getProperty("spring.flyway.user"), env.getProperty("spring.flyway.password")))
                .update("delete from stock_movement where warehouse_id in (?, ?)", src, dst);
        f.cleanUp();
    }

    private ResultActions call(String path, String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(post(path).contentType("application/json").content(body)
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf(mvc)).with(who));
    }

    private long dispatchableOrder(String qty) {
        var o = f.order(rep, src, Instant.now(), l(p, qty));
        tx.executeWithoutResult(s -> inventory.reserve(o));
        return fulfilment.create(new DeliveryRequest(o.orderId(), o.orderNumber(), o.repUserId(), src,
                DeliveryMethod.DELIVERY, "Cafe Uno", "Makati", o.lines().stream().map(x ->
                new DeliveryRequest.DeliveryLine(x.orderLineId(), x.productId(), "Beans", ProductUnit.KG, x.qty()))
                .toList()));
    }

    @Test
    void ledgerMatchesLevels() throws Exception {
        // receipt, then an increase and a decrease adjustment
        call("/api/stock/receipts", "{\"warehouseId\":" + src + ",\"supplierName\":\"Acme\",\"reference\":\"PO-1\","
                + "\"receivedOn\":\"2026-10-21\",\"lines\":[{\"productId\":" + p + ",\"qty\":20}]}", srcUser)
                .andExpect(status().isCreated());
        call("/api/stock/adjustments", "{\"warehouseId\":" + src + ",\"productId\":" + p
                + ",\"mode\":\"DELTA\",\"delta\":5,\"reason\":\"found\"}", srcUser).andExpect(status().isCreated());
        call("/api/stock/adjustments", "{\"warehouseId\":" + src + ",\"productId\":" + p
                + ",\"mode\":\"DELTA\",\"delta\":-2,\"reason\":\"spill\"}", srcUser).andExpect(status().isCreated());

        // a transfer that loses a unit in transit
        call("/api/transfers", "{\"fromWarehouseId\":" + src + ",\"toWarehouseId\":" + dst
                + ",\"notes\":\"n\",\"lines\":[{\"productId\":" + p + ",\"qty\":6}]}", admin)
                .andExpect(status().isCreated());
        long tr = jdbc.queryForObject("select max(id) from stock_transfer where from_warehouse_id = ?", Long.class, src);
        call("/api/transfers/" + tr + "/dispatch", "{}", srcUser).andExpect(status().isOk());
        long trLine = jdbc.queryForObject("select id from stock_transfer_line where transfer_id = ?", Long.class, tr);
        call("/api/transfers/" + tr + "/receive", "{\"lines\":[{\"lineId\":" + trLine + ",\"receivedQty\":5}]}",
                dstUser).andExpect(status().isOk());

        // an order that is short picked and dispatched, and one still holding a reservation
        long delivery = dispatchableOrder("4");
        dispatchableOrder("2");
        long doLine = jdbc.queryForObject("select id from delivery_order_line where delivery_order_id = ?",
                Long.class, delivery);
        call("/api/delivery-orders/" + delivery + "/pick",
                "{\"lines\":[{\"lineId\":" + doLine + ",\"pickedQty\":3}]}", srcUser).andExpect(status().isOk());
        // the gap is filled by a new receipt, then the full quantity is picked and dispatched
        call("/api/stock/receipts", "{\"warehouseId\":" + src + ",\"supplierName\":\"Acme\",\"reference\":\"PO-2\","
                + "\"receivedOn\":\"2026-10-21\",\"lines\":[{\"productId\":" + p + ",\"qty\":1}]}", srcUser)
                .andExpect(status().isCreated());
        call("/api/delivery-orders/" + delivery + "/pick",
                "{\"lines\":[{\"lineId\":" + doLine + ",\"pickedQty\":4}]}", srcUser).andExpect(status().isOk());
        call("/api/delivery-orders/" + delivery + "/dispatch", "{}", srcUser).andExpect(status().isOk());

        List<Map<String, Object>> levels = jdbc.queryForList("select l.on_hand, l.reserved, "
                + "(select coalesce(sum(m.qty), 0) from stock_movement m where m.product_id = l.product_id "
                + "and m.warehouse_id = l.warehouse_id) as ledger, "
                + "(select coalesce(sum(r.reserved_qty), 0) from stock_reservation r where r.product_id = l.product_id "
                + "and r.warehouse_id = l.warehouse_id) as held "
                + "from stock_level l where l.warehouse_id in (?, ?)", src, dst);
        assertThat(levels).hasSize(2);
        for (Map<String, Object> l : levels) {
            assertThat((BigDecimal) l.get("on_hand")).isEqualByComparingTo((BigDecimal) l.get("ledger"));
            assertThat((BigDecimal) l.get("reserved")).isEqualByComparingTo((BigDecimal) l.get("held"));
        }
        // src: 20 + 5 - 2 - 6 (transfer) - 1 (short pick) + 1 (receipt) - 4 (dispatch); dst: 5 received
        assertThat(f.onHand(p, src)).isEqualByComparingTo("13");
        assertThat(f.reserved(p, src)).isEqualByComparingTo("2");
        assertThat(f.onHand(p, dst)).isEqualByComparingTo("5");
    }

    @Test
    void returnRestockAndDamagedKeepLedgerConsistent() {
        f.receive(p, src, "10");
        tx.executeWithoutResult(s -> inventory.receiveReturns(
                List.of(new ReturnLine(p, src, new BigDecimal("3"), false)), "CM-T", 1));
        tx.executeWithoutResult(s -> inventory.receiveReturns(
                List.of(new ReturnLine(p, src, new BigDecimal("2"), true)), "CM-T", 1));

        assertThat(f.onHand(p, src)).isEqualByComparingTo("13");
        assertThat(jdbc.queryForObject("select sum(qty) from stock_movement where warehouse_id = ? and type = 'RETURN' "
                + "and ref_type = 'REFUND'", BigDecimal.class, src)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForList("select a.delta from stock_adjustment a where a.warehouse_id = ? "
                + "and a.source = 'RETURN_DAMAGED'", BigDecimal.class, src)).hasSize(1)
                .allSatisfy(d -> assertThat(d).isEqualByComparingTo("-2"));
        assertThat(jdbc.queryForObject("select count(*) from stock_movement where warehouse_id = ? "
                + "and type = 'ADJUSTMENT' and qty = -2 and ref_id in (select id from stock_adjustment "
                + "where source = 'RETURN_DAMAGED')", Integer.class, src)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(qty) from stock_movement where warehouse_id = ?",
                BigDecimal.class, src)).isEqualByComparingTo(f.onHand(p, src));
    }

    @Test
    void damagedReturnLeavesOtherOrdersReservationsAlone() {
        f.receive(p, src, "5");
        // The older order is short 2; the younger holds all 5. A RETURN that allocated would fill the older one,
        // and the write-off would then take 2 back from the younger one.
        var older = f.order(rep, src, Instant.now().minusSeconds(3600), l(p, "2"));
        var younger = f.order(rep, src, Instant.now(), l(p, "5"));
        tx.executeWithoutResult(s -> inventory.reserve(younger));
        tx.executeWithoutResult(s -> inventory.reserve(older));
        String held = "select reserved_qty || '/' || short_qty from stock_reservation where sales_order_id = ?";
        String olderBefore = jdbc.queryForObject(held, String.class, older.orderId());
        String youngerBefore = jdbc.queryForObject(held, String.class, younger.orderId());
        assertThat(olderBefore).isEqualTo("0.000/2.000");
        assertThat(youngerBefore).isEqualTo("5.000/0.000");

        tx.executeWithoutResult(s -> inventory.receiveReturns(
                List.of(new ReturnLine(p, src, new BigDecimal("2"), true)), "CM-T", 1));

        assertThat(jdbc.queryForObject(held, String.class, older.orderId())).isEqualTo(olderBefore);
        assertThat(jdbc.queryForObject(held, String.class, younger.orderId())).isEqualTo(youngerBefore);
        assertThat(jdbc.queryForObject("select count(*) from notification where type in "
                + "('ORDER_RESERVED','RESERVATION_TAKEN') and entity_id in (?, ?)", Integer.class,
                older.orderId(), younger.orderId())).isZero();
        assertThat(f.onHand(p, src)).isEqualByComparingTo("5");
        assertThat(f.reserved(p, src)).isEqualByComparingTo("5");
    }
}
