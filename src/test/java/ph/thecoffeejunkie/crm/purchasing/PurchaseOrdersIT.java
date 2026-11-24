package ph.thecoffeejunkie.crm.purchasing;

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
import ph.thecoffeejunkie.crm.shared.Role;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class PurchaseOrdersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    PurchasingFixture f;
    RequestPostProcessor admin;
    RequestPostProcessor warehouseA;
    long whA;
    long whB;
    long supplier;
    long product;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        whA = f.inv.warehouse("A");
        whB = f.inv.warehouse("B");
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        warehouseA = as(f.inv.warehouseUser("wh", whA), Role.WAREHOUSE, whA);
        supplier = f.supplier("S", null);
        product = f.inv.product("P1", "PC");
        jdbc.update("update product set cost = 12.345 where id = ?", product);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private String body(long warehouseId, String qty, String cost) {
        return "{\"supplierId\":" + supplier + (warehouseId == 0 ? "" : ",\"warehouseId\":" + warehouseId)
                + ",\"lines\":[{\"productId\":" + product + ",\"qty\":" + qty
                + (cost == null ? "" : ",\"unitCost\":" + cost) + "}]}";
    }

    private ResultActions create(RequestPostProcessor who, String json) throws Exception {
        return mvc.perform(post("/api/purchase-orders").contentType("application/json").content(json)
                .with(csrf(mvc)).with(who));
    }

    private long createPo(long warehouseId) throws Exception {
        String res = create(admin, body(warehouseId, "2", "10.00")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    private void setStatus(long id, String status) {
        jdbc.update("update purchase_order set status = ? where id = ?", status, id);
    }

    @Test
    void createDraftNumbersPoAndDefaultsCostAndWarehouse() throws Exception {
        if (jdbc.queryForList("select id from warehouse where is_default and archived_at is null").isEmpty()) {
            jdbc.update("update warehouse set is_default = true where id = ?", whA);
        }
        long dflt = jdbc.queryForObject("select id from warehouse where is_default and archived_at is null", Long.class);
        // 3 x 12.35 (cost rounded half-up to 2dp) = 37.05
        create(admin, body(0, "3", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("PO-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.warehouseId").value(dflt))
                .andExpect(jsonPath("$.supplierName").value("Supplier S"))
                .andExpect(jsonPath("$.lines[0].unitCost").value(12.35))
                .andExpect(jsonPath("$.total").value(37.05));
    }

    @Test
    void editOnlyInDraft() throws Exception {
        long id = createPo(whA);
        mvc.perform(put("/api/purchase-orders/" + id).contentType("application/json").content(body(whA, "5", "1.00"))
                .with(csrf(mvc)).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5.00));
        setStatus(id, "SENT");
        mvc.perform(put("/api/purchase-orders/" + id).contentType("application/json").content(body(whA, "5", "1.00"))
                .with(csrf(mvc)).with(admin)).andExpect(status().isConflict());
    }

    @Test
    void cancelFromDraftAndSent() throws Exception {
        long a = createPo(whA);
        mvc.perform(post("/api/purchase-orders/" + a + "/cancel").with(csrf(mvc)).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        long b = createPo(whA);
        setStatus(b, "SENT");
        mvc.perform(post("/api/purchase-orders/" + b + "/cancel").with(csrf(mvc)).with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/purchase-orders/" + b + "/cancel").with(csrf(mvc)).with(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelAfterReceiptIs409() throws Exception {
        long id = createPo(whA);
        setStatus(id, "PARTIALLY_RECEIVED");
        jdbc.update("update purchase_order_line set received_qty = 1 where po_id = ?", id);
        mvc.perform(post("/api/purchase-orders/" + id + "/cancel").with(csrf(mvc)).with(admin))
                .andExpect(status().isConflict());
        setStatus(id, "SENT");
        mvc.perform(post("/api/purchase-orders/" + id + "/cancel").with(csrf(mvc)).with(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedSupplierOrProductIs400() throws Exception {
        f.inv.archive(product);
        create(admin, body(whA, "1", "1")).andExpect(status().isBadRequest());
        long p2 = f.inv.product("P2", "PC");
        jdbc.update("update supplier set archived_at = now() where id = ?", supplier);
        create(admin, "{\"supplierId\":" + supplier + ",\"warehouseId\":" + whA + ",\"lines\":[{\"productId\":" + p2
                + ",\"qty\":1}]}").andExpect(status().isBadRequest());
    }

    @Test
    void warehouseUserSeesOnlyOwnWarehousePos() throws Exception {
        long a = createPo(whA);
        long b = createPo(whB);
        mvc.perform(get("/api/purchase-orders").param("supplierId", String.valueOf(supplier)).with(warehouseA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a));
        mvc.perform(get("/api/purchase-orders/" + a).with(warehouseA)).andExpect(status().isOk());
        mvc.perform(get("/api/purchase-orders/" + b).with(warehouseA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/purchase-orders").param("supplierId", String.valueOf(supplier)).param("status", "DRAFT")
                .with(admin)).andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void warehouseUserCannotCreate() throws Exception {
        create(warehouseA, body(whA, "1", "1")).andExpect(status().isForbidden());
        long id = createPo(whA);
        mvc.perform(post("/api/purchase-orders/" + id + "/cancel").with(csrf(mvc)).with(warehouseA))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyLinesIs400() throws Exception {
        create(admin, "{\"supplierId\":" + supplier + ",\"lines\":[]}").andExpect(status().isBadRequest());
    }
}
