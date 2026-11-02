package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class PosProductsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    SalesFixture f;
    long admin;
    long rep;
    long wh;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        wh = f.defaultWarehouse();
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
        setting("approval.discountPercentMax", "15");
        setting("pos.warehouseId", String.valueOf(wh));
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        setting("pos.warehouseId", "0");
    }

    private void setting(String key, String value) {
        jdbc.update("update app_setting set value = to_jsonb(?::numeric) where key = ?", value, key);
        caches.getCache("settings").clear();
    }

    private ResultActions list(RequestPostProcessor who, String q) throws Exception {
        return mvc.perform(get("/api/pos/products").param("q", q).with(who));
    }

    @Test
    void notConfiguredIs409() throws Exception {
        setting("pos.warehouseId", "0");
        list(asRep, "").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POS_NOT_CONFIGURED"));
    }

    @Test
    void archivedWarehouseIsNotConfigured() throws Exception {
        long archived = f.warehouse("ARC");
        jdbc.update("update warehouse set archived_at = now() where id = ?", archived);
        setting("pos.warehouseId", String.valueOf(archived));
        list(asRep, "").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POS_NOT_CONFIGURED"));
    }

    @Test
    void availableIsOnHandMinusReserved() throws Exception {
        long p = f.product("AV", "100.00", null, "VATABLE");
        f.stock(p, "10");
        long customer = f.customer("acme", rep);
        jdbc.update("update customer set credit_limit = 100000 where id = ?", customer);
        long order = f.createdOrder(asRep, customer, line(p, "3"));
        f.transition(asRep, order, "confirm", null).andExpect(status().isOk());
        String body = SalesFixture.body(list(asRep, f.tag + "AV").andExpect(status().isOk()));
        List<Number> available = JsonPath.read(body, "$.products.content[*].available");
        assertThat(available).hasSize(1);
        assertThat(new BigDecimal(available.get(0).toString())).isEqualByComparingTo("7");
    }

    @Test
    void minUnitPriceUsesCostOrCap() throws Exception {
        f.product("C90", "100.00", "90.00", "VATABLE");
        f.product("C50", "100.00", "50.00", "VATABLE");
        f.product("CNL", "100.00", null, "VATABLE");
        f.product("CRD", "33.33", null, "VATABLE");
        String body = SalesFixture.body(list(asRep, f.tag + "C").andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCapPct").value(15)));
        assertThat(min(body, "C90")).isEqualByComparingTo("90.00");
        assertThat(min(body, "C50")).isEqualByComparingTo("85.00");
        assertThat(min(body, "CNL")).isEqualByComparingTo("85.00");
        assertThat(min(body, "CRD")).isEqualByComparingTo("28.33");
    }

    @Test
    void noMatchReturnsEmptyPage() throws Exception {
        list(asRep, f.tag + "NOPE").andExpect(status().isOk()).andExpect(jsonPath("$.products.content").isEmpty());
    }

    private BigDecimal min(String body, String sku) {
        List<Number> v = JsonPath.read(body, "$.products.content[?(@.sku=='" + f.tag + sku + "')].minUnitPrice");
        return new BigDecimal(v.get(0).toString());
    }

    @Test
    void noCostInResponse() throws Exception {
        f.product("NC", "100.00", "90.00", "VATABLE");
        String body = SalesFixture.body(list(asAdmin, f.tag + "NC").andExpect(status().isOk()));
        assertThat(body).contains(f.tag + "NC").doesNotContain("cost");
    }

    @Test
    void searchesNameAndSkuAndSkipsArchived() throws Exception {
        f.product("SK1", "10.00", null, "VATABLE");
        long gone = f.product("SK2", "10.00", null, "VATABLE");
        jdbc.update("update product set archived_at = now() where id = ?", gone);
        String body = SalesFixture.body(list(asRep, f.tag.toLowerCase() + "sk").andExpect(status().isOk()));
        List<String> skus = JsonPath.read(body, "$.products.content[*].sku");
        assertThat(skus).containsExactly(f.tag + "SK1");
    }
}
