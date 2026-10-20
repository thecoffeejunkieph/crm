package ph.thecoffeejunkie.crm.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class StockReadsIT {

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

    private void shortOrder(long product, String have, String want) {
        f.receive(product, wh1, have);
        inventory.reserve(f.order(rep, wh1, Instant.now(), l(product, want)));
    }

    @Test
    void availableIsOnHandMinusReserved() throws Exception {
        shortOrder(p, "10", "4");
        mvc.perform(get("/api/stock").param("warehouseId", "" + wh1).param("q", f.tag).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(p))
                .andExpect(jsonPath("$.content[0].unit").value("KG"))
                .andExpect(jsonPath("$.content[0].onHand").value(10.0))
                .andExpect(jsonPath("$.content[0].reserved").value(4.0))
                .andExpect(jsonPath("$.content[0].available").value(6.0))
                .andExpect(jsonPath("$.content[0].short").value(0.0))
                .andExpect(jsonPath("$.content[0].archived").value(false));
    }

    @Test
    void lowOnlyListsShortProducts() throws Exception {
        long other = f.product("Q", "PC");
        shortOrder(p, "3", "5");
        f.receive(other, wh1, "5");
        mvc.perform(get("/api/stock").param("warehouseId", "" + wh1).param("q", f.tag)
                        .param("lowOnly", "true").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(p))
                .andExpect(jsonPath("$.content[0].available").value(0.0))
                .andExpect(jsonPath("$.content[0].short").value(2.0));
    }

    @Test
    void warehouseUserSeesOnlyAssigned() throws Exception {
        f.receive(p, wh1, "1");
        f.receive(p, wh2, "1");
        mvc.perform(get("/api/stock").param("warehouseId", "" + wh1).param("q", f.tag).with(warehouse1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/stock").param("warehouseId", "" + wh2).with(warehouse1))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/stock/" + p + "/movements").param("warehouseId", "" + wh2).with(warehouse1))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/stock").with(warehouse1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void salesReadsAnyWarehouse() throws Exception {
        f.receive(p, wh2, "2");
        mvc.perform(get("/api/stock").param("warehouseId", "" + wh2).param("q", f.tag).with(sales))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].onHand").value(2.0));
        mvc.perform(get("/api/stock").param("q", f.tag).with(sales))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    private String receive(String qty) throws Exception {
        String body = "{\"warehouseId\":" + wh1 + ",\"supplierName\":\"Acme\",\"receivedOn\":\"2026-10-21\","
                + "\"lines\":[{\"productId\":" + p + ",\"qty\":" + qty + "}]}";
        return mvc.perform(post("/api/stock/receipts").contentType("application/json").content(body)
                        .with(csrf(mvc)).with(admin)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void movementsNewestFirstWithRefNumber() throws Exception {
        receive("2");
        receive("3");
        String number = jdbc.queryForObject("select max(number) from stock_receipt where warehouse_id = ?",
                String.class, wh1);
        mvc.perform(get("/api/stock/" + p + "/movements").param("warehouseId", "" + wh1).with(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].qty").value(3.0))
                .andExpect(jsonPath("$.content[0].type").value("RECEIPT"))
                .andExpect(jsonPath("$.content[0].refType").value("STOCK_RECEIPT"))
                .andExpect(jsonPath("$.content[0].refNumber").value(number))
                .andExpect(jsonPath("$.content[0].createdByName").isNotEmpty())
                .andExpect(jsonPath("$.content[1].qty").value(2.0));
    }

    @Test
    void availabilityPerWarehouse() throws Exception {
        shortOrder(p, "5", "2");
        f.receive(p, wh2, "7");
        mvc.perform(get("/api/products/" + p + "/availability").with(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.warehouseId==" + wh1 + ")].onHand").value(5.0))
                .andExpect(jsonPath("$[?(@.warehouseId==" + wh1 + ")].available").value(3.0))
                .andExpect(jsonPath("$[?(@.warehouseId==" + wh2 + ")].available").value(7.0));
        mvc.perform(get("/api/products/" + p + "/availability").with(warehouse1))
                .andExpect(status().isForbidden());
    }

    @Test
    void costNeverInBody() throws Exception {
        jdbc.update("update product set cost = 77.77 where id = ?", p);
        shortOrder(p, "5", "2");
        receive("1");
        for (String url : new String[]{"/api/stock?warehouseId=" + wh1, "/api/stock/" + p + "/movements",
                "/api/products/" + p + "/availability", "/api/stock/receipts?warehouseId=" + wh1}) {
            String body = mvc.perform(get(url).with(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(url).doesNotContainIgnoringCase("cost").doesNotContain("77.77");
        }
        String created = receive("1");
        assertThat(created).doesNotContainIgnoringCase("cost");
    }
}
