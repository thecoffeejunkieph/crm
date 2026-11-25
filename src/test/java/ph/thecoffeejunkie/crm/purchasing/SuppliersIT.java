package ph.thecoffeejunkie.crm.purchasing;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class SuppliersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    PurchasingFixture f;
    RequestPostProcessor admin;
    RequestPostProcessor sales;
    RequestPostProcessor warehouse;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        admin = as(f.inv.admin("adm"), Role.ADMIN);
        sales = as(f.inv.user("rep"), Role.SALES);
        warehouse = as(f.inv.user("wh"), Role.WAREHOUSE, f.inv.warehouse("A"));
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private String body(String code, String limit) {
        return "{\"code\":\"" + f.code(code) + "\",\"name\":\"Acme\",\"creditLimit\":" + limit + "}";
    }

    private org.springframework.test.web.servlet.ResultActions create(String code, String limit) throws Exception {
        return mvc.perform(post("/api/suppliers").contentType("application/json").content(body(code, limit))
                .with(csrf(mvc)).with(admin));
    }

    @Test
    void adminCreatesAndListsSupplier() throws Exception {
        create("S1", "1000").andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(f.code("S1")));
        mvc.perform(get("/api/suppliers").param("q", f.code("S1")).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value(f.code("S1")));
    }

    @Test
    void duplicateCodeIs409() throws Exception {
        create("S2", "null").andExpect(status().isCreated());
        create("S2", "null").andExpect(status().isConflict());
    }

    @Test
    void negativeCreditLimitIs400() throws Exception {
        create("S3", "-1").andExpect(status().isBadRequest());
    }

    @Test
    void archivedSupplierHiddenFromDefaultList() throws Exception {
        long id = f.supplier("S4", null);
        mvc.perform(post("/api/suppliers/" + id + "/archive").with(csrf(mvc)).with(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/suppliers").param("q", f.code("S4")).with(admin))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/suppliers").param("q", f.code("S4")).param("includeArchived", "true").with(admin))
                .andExpect(jsonPath("$.content[0].archived").value(true));
    }

    @Test
    void downpaymentPercentRoundTrips() throws Exception {
        String res = mvc.perform(post("/api/suppliers").contentType("application/json")
                .content("{\"code\":\"" + f.code("DP") + "\",\"name\":\"Acme\",\"downpaymentPercent\":50}")
                .with(csrf(mvc)).with(admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.downpaymentPercent").value(50.00)).andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(res, "$.id");
        mvc.perform(get("/api/suppliers/" + id).with(admin)).andExpect(jsonPath("$.downpaymentPercent").value(50.00));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/suppliers/" + id)
                .contentType("application/json")
                .content("{\"code\":\"" + f.code("DP") + "\",\"name\":\"Acme\",\"downpaymentPercent\":101,\"version\":0}")
                .with(csrf(mvc)).with(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void salesAndWarehouseGet403() throws Exception {
        mvc.perform(get("/api/suppliers").with(sales)).andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers").with(warehouse)).andExpect(status().isForbidden());
    }
}
