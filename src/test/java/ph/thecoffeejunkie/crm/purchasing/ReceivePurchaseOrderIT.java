package ph.thecoffeejunkie.crm.purchasing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.inventory.Inventory;
import ph.thecoffeejunkie.crm.inventory.OrderStock;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class ReceivePurchaseOrderIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired CreditQuery credit;
    @Autowired Inventory inventory;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long otherWh;
    long product;
    long supplier;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        wh = f.inv.warehouse("A");
        otherWh = f.inv.warehouse("B");
        product = f.inv.product("P1", "PC");
        supplier = f.supplier("S", new BigDecimal("100000"));
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** A PO of qty x 10.00 for the product; sent unless draft. Returns {poId, lineId}. */
    private long[] po(String qty, boolean send) throws Exception {
        String json = "{\"supplierId\":" + supplier + ",\"warehouseId\":" + wh + ",\"lines\":[{\"productId\":" + product
                + ",\"qty\":" + qty + ",\"unitCost\":10.00}]}";
        String res = mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        long line = ((Number) JsonPath.read(res, "$.lines[0].id")).longValue();
        if (send) {
            mvc.perform(post("/api/purchase-orders/" + id + "/send").with(csrf(mvc)).with(admin))
                    .andExpect(jsonPath("$.status").value("SENT"));
        }
        return new long[]{id, line};
    }

    private ResultActions receive(long po, long line, String qty, RequestPostProcessor who) throws Exception {
        return receiveBody(po, "[{\"lineId\":" + line + ",\"qty\":" + qty + "}]", who);
    }

    private ResultActions receiveBody(long po, String lines, RequestPostProcessor who) throws Exception {
        String body = "{\"receivedOn\":\"2026-11-25\",\"reference\":\"DR-77\",\"lines\":" + lines + "}";
        return mvc.perform(post("/api/purchase-orders/" + po + "/receipts").contentType("application/json")
                .content(body).with(csrf(mvc)).with(who));
    }

    private ResultActions close(long po, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/api/purchase-orders/" + po + "/close").with(csrf(mvc)).with(who));
    }

    private BigDecimal receivedQty(long line) {
        return jdbc.queryForObject("select received_qty from purchase_order_line where id = ?", BigDecimal.class, line);
    }

    /** The invariant credit and the supplier statement both rely on. */
    private BigDecimal receiptLineSum(long po) {
        return jdbc.queryForObject("select coalesce(sum(l.qty), 0) from stock_receipt_line l join stock_receipt r "
                + "on r.id = l.receipt_id where r.purchase_order_id = ?", BigDecimal.class, po);
    }

    @Test
    void partialReceiveCreatesLinkedReceiptAndRaisesStock() throws Exception {
        long[] p = po("10", true);
        receive(p[0], p[1], "4", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.lines[0].receivedQty").value(4.0));
        assertThat(jdbc.queryForObject("select supplier_name || '|' || reference || '|' || warehouse_id from stock_receipt "
                + "where purchase_order_id = ?", String.class, p[0])).isEqualTo("Supplier S|DR-77|" + wh);
        assertThat(f.inv.onHand(product, wh)).isEqualByComparingTo("4");
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo(receivedQty(p[1]));
    }

    @Test
    void fullReceiveIsReceived() throws Exception {
        long[] p = po("10", true);
        receive(p[0], p[1], "4", admin).andExpect(status().isOk());
        receive(p[0], p[1], "6", admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.closedShort").value(false));
        assertThat(f.inv.onHand(product, wh)).isEqualByComparingTo("10");
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("10");
        receive(p[0], p[1], "1", admin).andExpect(status().isConflict());
    }

    @Test
    void overReceiveIs409() throws Exception {
        long[] p = po("10", true);
        receive(p[0], p[1], "4", admin).andExpect(status().isOk());
        String number = jdbc.queryForObject("select number from purchase_order where id = ?", String.class, p[0]);
        receive(p[0], p[1], "6.001", admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString(number)));
        assertThat(receivedQty(p[1])).isEqualByComparingTo("4");
        assertThat(f.inv.onHand(product, wh)).isEqualByComparingTo("4");
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("4");
    }

    @Test
    void unknownOrDuplicateLineIs400() throws Exception {
        long[] p = po("10", true);
        receiveBody(p[0], "[{\"lineId\":-1,\"qty\":1}]", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].lineId"));
        receiveBody(p[0], "[{\"lineId\":" + p[1] + ",\"qty\":1},{\"lineId\":" + p[1] + ",\"qty\":1}]", admin)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lines[1].lineId"));
        receive(p[0], p[1], "0", admin).andExpect(status().isBadRequest());
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("0");
    }

    @Test
    void idempotentRetryReceivesOnce() throws Exception {
        long[] p = po("10", true);
        String key = UUID.randomUUID().toString();
        String body = "{\"receivedOn\":\"2026-11-25\",\"lines\":[{\"lineId\":" + p[1] + ",\"qty\":3}]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/purchase-orders/" + p[0] + "/receipts").contentType("application/json").content(body)
                    .header("Idempotency-Key", key).with(csrf(mvc)).with(admin)).andExpect(status().isOk())
                    .andExpect(header().string("Idempotent-Replayed", i == 0 ? null : "true"));
        }
        assertThat(receivedQty(p[1])).isEqualByComparingTo("3");
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("select count(*) from stock_receipt where purchase_order_id = ?", Integer.class,
                p[0])).isEqualTo(1);
    }

    @Test
    void receiveDraftIs409() throws Exception {
        long[] p = po("10", false);
        receive(p[0], p[1], "1", admin).andExpect(status().isConflict());
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("0");
    }

    @Test
    void warehouseUserReceivesOwnWarehouseOnly() throws Exception {
        long[] p = po("10", true);
        RequestPostProcessor other = as(f.inv.warehouseUser("whB", otherWh), Role.WAREHOUSE, otherWh);
        receive(p[0], p[1], "1", other).andExpect(status().isNotFound());
        RequestPostProcessor own = as(f.inv.warehouseUser("whA", wh), Role.WAREHOUSE, wh);
        receive(p[0], p[1], "1", own).andExpect(status().isOk());
        assertThat(receivedQty(p[1])).isEqualByComparingTo("1");
        close(p[0], own).andExpect(status().isForbidden());
    }

    @Test
    void closeShortReleasesCommitted() throws Exception {
        long[] p = po("10", true);
        close(p[0], admin).andExpect(status().isConflict()); // SENT, nothing received
        receive(p[0], p[1], "4", admin).andExpect(status().isOk());
        assertThat(credit.forSuppliers(List.of(supplier)).get(supplier).committed()).isEqualByComparingTo("60.00");
        close(p[0], admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.closedShort").value(true));
        SupplierCredit c = credit.forSuppliers(List.of(supplier)).get(supplier);
        assertThat(c.committed()).isEqualByComparingTo("0");
        assertThat(c.balance()).isEqualByComparingTo("40.00");
        receive(p[0], p[1], "1", admin).andExpect(status().isConflict());
    }

    @Test
    void concurrentReceivesCannotOverReceive() throws Exception {
        long[] p = po("10", true);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                gate.await();
                return receive(p[0], p[1], "10", admin).andReturn().getResponse().getStatus();
            }));
        }
        gate.countDown();
        List<Integer> codes = new ArrayList<>();
        for (var r : results) {
            codes.add(r.get());
        }
        pool.shutdown();
        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(receivedQty(p[1])).isEqualByComparingTo("10");
        assertThat(receiptLineSum(p[0])).isEqualByComparingTo("10");
        assertThat(f.inv.onHand(product, wh)).isEqualByComparingTo("10");
    }

    @Test
    void receiptFillsShortReservations() throws Exception {
        long rep = f.inv.user("rep");
        OrderStock o = f.inv.order(rep, wh, Instant.now(), l(product, "3"));
        inventory.reserve(o);
        assertThat(inventory.totalShort(o.orderId())).isEqualByComparingTo("3");
        long[] p = po("10", true);
        receive(p[0], p[1], "5", admin).andExpect(status().isOk());
        assertThat(inventory.totalShort(o.orderId())).isEqualByComparingTo("0");
        assertThat(f.inv.reserved(product, wh)).isEqualByComparingTo("3");
    }
}
