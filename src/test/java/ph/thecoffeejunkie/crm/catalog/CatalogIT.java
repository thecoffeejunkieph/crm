package ph.thecoffeejunkie.crm.catalog;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class CatalogIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Catalog catalog;

    private final String tag = "CA" + UUID.randomUUID().toString().substring(0, 8);
    long admin;
    long sales;

    @BeforeEach
    void users() {
        admin = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, 'A', 'D', 'ADMIN') returning id",
                Long.class, tag + "a@ca.it");
        sales = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, 'S', 'R', 'SALES') returning id",
                Long.class, tag + "s@ca.it");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from product where sku like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private static UsernamePasswordAuthenticationToken as(long id, Role role) {
        return new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@ca.it", "A U", role, Set.of()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String body(String sku, String price, Long version) {
        return "{\"sku\":\"%s\",\"name\":\"%s beans\",\"unit\":\"KG\",\"price\":%s,\"cost\":7.50,\"taxClass\":\"VATABLE\",\"reorderLevel\":25%s}"
                .formatted(sku, tag, price, version == null ? "" : ",\"version\":" + version);
    }

    private ResultActions create(long uid, Role role, String sku, String price) throws Exception {
        return mvc.perform(post("/api/products").with(authentication(as(uid, role))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body(sku, price, null)));
    }

    private long createdId(String sku, String price) throws Exception {
        String res = create(admin, Role.ADMIN, sku, price).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void salesListHasNoCostField() throws Exception {
        createdId(tag + "-1", "10.00");
        mvc.perform(get("/api/products?q=" + tag).with(authentication(as(sales, Role.SALES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value(tag + "-1"))
                .andExpect(jsonPath("$.content[0].cost").doesNotExist());
    }

    @Test
    void adminSeesCost() throws Exception {
        long id = createdId(tag + "-1", "10.00");
        mvc.perform(get("/api/products/" + id).with(authentication(as(admin, Role.ADMIN))))
                .andExpect(jsonPath("$.cost").value(7.50))
                .andExpect(jsonPath("$.reorderLevel").value(25));
    }

    @Test
    void salesCannotCreate() throws Exception {
        create(sales, Role.SALES, tag + "-1", "10.00").andExpect(status().isForbidden());
    }

    @Test
    void duplicateSkuCaseInsensitive409() throws Exception {
        createdId(tag + "-ab", "10.00");
        create(admin, Role.ADMIN, tag.toLowerCase() + "-AB", "10.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void archivedHiddenUnlessRequested() throws Exception {
        long id = createdId(tag + "-1", "10.00");
        mvc.perform(post("/api/products/" + id + "/archive").with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/products?q=" + tag).with(authentication(as(sales, Role.SALES))))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products?includeArchived=true&q=" + tag).with(authentication(as(sales, Role.SALES))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].archivedAt").isNotEmpty());
    }

    @Test
    void priceChangeAudited() throws Exception {
        long id = createdId(tag + "-1", "10.00");
        mvc.perform(put("/api/products/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body(tag + "-1", "12", 0L))).andExpect(status().isOk());
        String changes = jdbc.queryForObject("select changes::text from audit_event where entity_type = 'PRODUCT' "
                + "and entity_id = ? and action = 'UPDATED'", String.class, id);
        assertThat(JsonPath.<List<String>>read(changes, "$.price")).containsExactly("10.00", "12.00");
    }

    @Test
    void staleVersion409() throws Exception {
        long id = createdId(tag + "-1", "10.00");
        mvc.perform(put("/api/products/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body(tag + "-1", "12", 5L)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void infoIncludesArchived() throws Exception {
        long id = createdId(tag + "-1", "10.00");
        jdbc.update("update product set archived_at = now() where id = ?", id);
        ProductInfo info = catalog.info(List.of(id)).get(id);
        assertThat(info.archived()).isTrue();
        assertThat(info.cost()).isEqualByComparingTo("7.50");
        assertThat(info.unit()).isEqualTo(ProductUnit.KG);
    }

    private ResultActions importCsv(long uid, Role role, String csv, boolean dryRun) throws Exception {
        var file = new MockMultipartFile("file", "p.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return mvc.perform(multipart("/api/products/import").file(file).param("dryRun", String.valueOf(dryRun))
                .with(authentication(as(uid, role))).with(csrf(mvc)));
    }

    @Test
    void importChecksEveryRowThenCreatesAllAndExportsWithoutCostForSales() throws Exception {
        String bad = "sku,name,unit,price,cost,taxClass\n%s-i1,%s beans,KG,10,5,VATABLE\n%s-i1,Dup,KG,1,,\n".formatted(tag, tag, tag);
        importCsv(admin, Role.ADMIN, bad, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.errors[0].row").value(3));
        String good = "sku,name,unit,price\n%s-i1,%s beans,KG,10\n%s-i2,%s cups,pc,2.50\n".formatted(tag, tag, tag, tag);
        importCsv(admin, Role.ADMIN, good, true).andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.errors").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from product where sku like ?", Long.class, tag + "%")).isZero();
        importCsv(admin, Role.ADMIN, good, false).andExpect(jsonPath("$.created").value(2));
        importCsv(sales, Role.SALES, good, true).andExpect(status().isForbidden());

        mvc.perform(get("/api/products/export?format=csv").with(authentication(as(sales, Role.SALES))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(tag + "-i2")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(",cost,"))));
        mvc.perform(get("/api/products/export?format=xlsx").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
