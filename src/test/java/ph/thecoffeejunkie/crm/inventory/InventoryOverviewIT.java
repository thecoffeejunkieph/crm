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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class InventoryOverviewIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long wh1;
    long wh2;
    long p;
    long rep;

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        wh1 = f.warehouse("A");
        wh2 = f.warehouse("B");
        p = f.product("P", "KG");
        jdbc.update("update product set cost = 50, reorder_level = 6 where id = ?", p);
        f.receive(p, wh1, "10");
        inventory.reserve(f.order(rep, wh1, Instant.now(), l(p, "4")));
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private String row(String field) {
        return "$.products[?(@.productId == " + p + ")]." + field;
    }

    @Test
    void lowWhenAvailableAtThresholdAndValuedAtCostForAdmin() throws Exception {
        mvc.perform(get("/api/inventory/overview").with(as(f.user("adm"), Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row("onHand")).value(contains(10.0)))
                .andExpect(jsonPath(row("available")).value(contains(6.0)))
                .andExpect(jsonPath(row("health")).value(contains("LOW")))
                .andExpect(jsonPath(row("value")).value(contains(500.0)))
                .andExpect(jsonPath(row("slow")).value(contains(true)))
                .andExpect(jsonPath("$.totalValue").isNumber())
                .andExpect(jsonPath("$.byWarehouse[?(@.warehouseId == " + wh1 + ")].value").value(contains(500.0)));
    }

    @Test
    void salesSeesQuantitiesButNoCost() throws Exception {
        mvc.perform(get("/api/inventory/overview").with(as(rep, Role.SALES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row("health")).value(contains("LOW")))
                .andExpect(jsonPath(row("unitCost")).value(empty()))
                .andExpect(jsonPath(row("value")).value(empty()))
                .andExpect(jsonPath("$.totalValue").doesNotExist())
                .andExpect(jsonPath("$.byWarehouse").doesNotExist());
    }

    @Test
    void warehouseUserSeesOnlyTheirWarehouse() throws Exception {
        mvc.perform(get("/api/inventory/overview").with(as(f.user("wh"), Role.WAREHOUSE, wh2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row("onHand")).value(contains(0.0)))
                .andExpect(jsonPath(row("health")).value(contains("OUT")));
        mvc.perform(get("/api/inventory/overview").param("warehouseId", "" + wh1)
                        .with(as(f.user("wh2"), Role.WAREHOUSE, wh2)))
                .andExpect(status().isNotFound());
    }
}
