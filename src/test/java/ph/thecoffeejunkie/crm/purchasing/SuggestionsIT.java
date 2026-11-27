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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class SuggestionsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    PurchasingFixture f;
    RequestPostProcessor admin;
    long wh;
    long supplier;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        wh = f.inv.warehouse("A");
        admin = as(f.inv.admin("adm"), Role.ADMIN, wh);
        supplier = f.supplier("S", null);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** Product with level 10, target 30, on hand 4 (so suggested 26) and the given preferred supplier. */
    private long product(String sku, Long supplierId) {
        long id = f.inv.product(sku, "PC");
        jdbc.update("update product set reorder_level = 10, reorder_target = 30, preferred_supplier_id = ? where id = ?",
                supplierId, id);
        f.inv.receive(id, wh, "4");
        return id;
    }

    private String sku(String s) {
        return f.inv.tag + s;
    }

    private String json() throws Exception {
        return mvc.perform(get("/api/purchasing/suggestions").with(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> item(String json, String s) {
        List<Map<String, Object>> l = JsonPath.read(json, "$.groups[*].items[?(@.sku=='" + sku(s) + "')]");
        return l.isEmpty() ? null : l.get(0);
    }

    private long draftPo(long productId, String qty) {
        long po = jdbc.queryForObject("insert into purchase_order (number, supplier_id, warehouse_id, status) "
                + "values (?, ?, ?, 'DRAFT') returning id", Long.class,
                f.inv.tag + "D" + System.nanoTime(), supplier, wh);
        jdbc.update("insert into purchase_order_line (po_id, product_id, qty, unit_cost) values (?, ?, ?::numeric, 1)",
                po, productId, qty);
        return po;
    }

    @Test
    void groupsByPreferredSupplierWithNoSupplierGroup() throws Exception {
        product("A", supplier);
        product("B", null);
        String j = json();
        List<Object> ids = JsonPath.read(j, "$.groups[*].supplierId");
        assertThat(ids.get(ids.size() - 1)).isNull();
        List<Map<String, Object>> mine = JsonPath.read(j, "$.groups[?(@.supplierId==" + supplier + ")]");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).containsEntry("supplierName", "Supplier S");
        assertThat(item(j, "A")).containsEntry("suggestedQty", 26.0).containsEntry("available", 4.0);
        assertThat(item(j, "B")).isNotNull();
        List<List<String>> none = JsonPath.read(j, "$.groups[?(@.supplierId==null)].items[*].sku");
        assertThat(none.toString()).contains(sku("B")).doesNotContain(sku("A"));
    }

    @Test
    void productWithoutTargetNotSuggested() throws Exception {
        long id = product("A", supplier);
        jdbc.update("update product set reorder_target = null where id = ?", id);
        assertThat(item(json(), "A")).isNull();
    }

    @Test
    void archivedProductNotSuggested() throws Exception {
        long id = product("A", supplier);
        jdbc.update("update product set archived_at = now() where id = ?", id);
        assertThat(item(json(), "A")).isNull();
    }

    @Test
    void pendingApprovalPoCountsAsIncoming() throws Exception {
        long id = product("A", supplier);
        long po = draftPo(id, "30");
        jdbc.update("update purchase_order set status = 'PENDING_APPROVAL' where id = ?", po);
        assertThat(item(json(), "A")).isNull(); // 4 + 30 > level
        jdbc.update("update purchase_order set closed_short = true where id = ?", po);
        assertThat(item(json(), "A")).containsEntry("incoming", 0.0);
    }

    @Test
    void draftPoFlaggedOnDraftPo() throws Exception {
        long id = product("A", supplier);
        draftPo(id, "5");
        long newer = draftPo(id, "7");
        String number = jdbc.queryForObject("select number from purchase_order where id = ?", String.class, newer);
        assertThat(item(json(), "A")).containsEntry("onDraftPo", number).containsEntry("incoming", 0.0);
    }

    @Test
    void velocityFromLast30DaysDispatch() throws Exception {
        long id = product("A", supplier);
        jdbc.update("insert into stock_movement (product_id, warehouse_id, type, qty, created_at) "
                + "values (?, ?, 'DISPATCH', -60, now() - interval '10 days')", id, wh);
        jdbc.update("insert into stock_movement (product_id, warehouse_id, type, qty, created_at) "
                + "values (?, ?, 'DISPATCH', -90, now() - interval '31 days')", id, wh);
        // 60 / 30 = 2.000 per day; cover 4 / 2 = 2.0 days
        assertThat(item(json(), "A")).containsEntry("perDay", 2.0).containsEntry("coverDays", 2.0);
    }

    @Test
    void zeroVelocityCoverDaysNull() throws Exception {
        product("A", supplier);
        assertThat(item(json(), "A")).containsEntry("perDay", 0.0).containsEntry("coverDays", null);
    }

    @Test
    void draftFromSuggestionsCreatesDraftPo() throws Exception {
        if (jdbc.queryForList("select id from warehouse where is_default and archived_at is null").isEmpty()) {
            jdbc.update("update warehouse set is_default = true where id = ?", wh);
        }
        long id = product("A", supplier);
        jdbc.update("update product set cost = 3.50 where id = ?", id);
        mvc.perform(post("/api/purchasing/suggestions/draft").contentType("application/json")
                        .content("{\"supplierId\":" + supplier + ",\"lines\":[{\"productId\":" + id + ",\"qty\":26}]}")
                        .with(csrf(mvc)).with(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines[0].qty").value(26.0))
                .andExpect(jsonPath("$.lines[0].unitCost").value(3.5));
        assertThat(item(json(), "A").get("onDraftPo")).isNotNull();
    }
}
