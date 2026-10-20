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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class ReceiptsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long rep;
    long wh1;
    long wh2;
    long p;
    RequestPostProcessor admin;
    RequestPostProcessor sales;
    RequestPostProcessor warehouse1;

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        wh1 = f.warehouse("A");
        wh2 = f.warehouse("B");
        p = f.product("P", "KG");
        admin = as(f.user("adm"), Role.ADMIN);
        sales = as(rep, Role.SALES);
        warehouse1 = as(f.user("wh"), Role.WAREHOUSE, wh1);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions receive(long warehouse, String qty, RequestPostProcessor who, String key) throws Exception {
        String body = "{\"warehouseId\":" + warehouse + ",\"supplierName\":\"Acme\",\"reference\":\"PO-1\","
                + "\"receivedOn\":\"2026-10-21\",\"lines\":[{\"productId\":" + p + ",\"qty\":" + qty + "}]}";
        return mvc.perform(post("/api/stock/receipts").contentType("application/json").content(body)
                .header("Idempotency-Key", key).with(csrf(mvc)).with(who));
    }

    private ResultActions receive(long warehouse, String qty, RequestPostProcessor who) throws Exception {
        return receive(warehouse, qty, who, UUID.randomUUID().toString());
    }

    private int receipts() {
        return jdbc.queryForObject("select count(*) from stock_receipt where warehouse_id in (?, ?)", Integer.class,
                wh1, wh2);
    }

    @Test
    void receiptRaisesOnHandAndAllocates() throws Exception {
        f.receive(p, wh1, "2");
        OrderStock o = f.order(rep, wh1, Instant.now(), l(p, "5"));
        inventory.reserve(o);
        assertThat(inventory.totalShort(o.orderId())).isEqualByComparingTo("3");

        receive(wh1, "4", warehouse1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].qty").value(4.0))
                .andExpect(jsonPath("$.lines[0].sku").value(f.tag + "P"))
                .andExpect(jsonPath("$.supplierName").value("Acme"));

        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("6");
        assertThat(f.reserved(p, wh1)).isEqualByComparingTo("5");
        assertThat(inventory.totalShort(o.orderId())).isEqualByComparingTo("0");
    }

    @Test
    void numberFormatSR() throws Exception {
        jdbc.update("update document_counter set next_value = 1 where name = 'STOCK_RECEIPT'");
        int year = LocalDate.now(ZoneId.of("Asia/Manila")).getYear();
        receive(wh1, "1", admin).andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("SR-" + year + "-00001"));
        receive(wh1, "1", admin).andExpect(jsonPath("$.number").value("SR-" + year + "-00002"));
        mvc.perform(get("/api/stock/receipts").param("warehouseId", "" + wh1).with(warehouse1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].number").value("SR-" + year + "-00002"));
        long id = jdbc.queryForObject("select id from stock_receipt where number = ?", Long.class,
                "SR-" + year + "-00001");
        mvc.perform(get("/api/stock/receipts/" + id).with(warehouse1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reference").value("PO-1"));
    }

    @Test
    void salesCannotReceive() throws Exception {
        receive(wh1, "1", sales).andExpect(status().isForbidden());
        mvc.perform(get("/api/stock/receipts").with(sales)).andExpect(status().isForbidden());
        assertThat(receipts()).isZero();
    }

    @Test
    void warehouseUserOtherWarehouse404() throws Exception {
        receive(wh2, "1", warehouse1).andExpect(status().isNotFound());
        assertThat(receipts()).isZero();
        receive(wh2, "1", admin).andExpect(status().isCreated());
        long id = jdbc.queryForObject("select id from stock_receipt where warehouse_id = ?", Long.class, wh2);
        mvc.perform(get("/api/stock/receipts/" + id).with(warehouse1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/stock/receipts").param("warehouseId", "" + wh2).with(warehouse1))
                .andExpect(status().isNotFound());
    }

    @Test
    void qtyWithFourDecimals400() throws Exception {
        receive(wh1, "1.2345", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        receive(wh1, "0", admin).andExpect(status().isBadRequest());
        assertThat(receipts()).isZero();
    }

    @Test
    void archivedProductKeepsStockButRejectsReceipt() throws Exception {
        f.receive(p, wh1, "5");
        f.archive(p);

        mvc.perform(get("/api/stock").param("warehouseId", "" + wh1).param("q", f.tag).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].archived").value(true))
                .andExpect(jsonPath("$.content[0].onHand").value(5.0));
        receive(wh1, "1", admin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines[0].productId"));
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("5");
    }

    @Test
    void idempotentRetryCreatesOneReceipt() throws Exception {
        String key = UUID.randomUUID().toString();
        receive(wh1, "2", admin, key).andExpect(status().isCreated());
        receive(wh1, "2", admin, key).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(receipts()).isEqualTo(1);
        assertThat(f.onHand(p, wh1)).isEqualByComparingTo("2");
    }
}
