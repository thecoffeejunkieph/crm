package ph.thecoffeejunkie.crm.customers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class LeadSourcesIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    final String tag = "LS" + UUID.randomUUID().toString().substring(0, 6);
    long admin;
    long rep;
    long wh;

    @BeforeEach
    void seed() {
        admin = user("adm", Role.ADMIN);
        rep = user("rep", Role.SALES);
        wh = user("wh", Role.WAREHOUSE);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from lead_source where name like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, ?, 'U', ?) "
                + "returning id", Long.class, tag + name + "@ls.it", name, role.name());
    }

    private static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@ls.it",
                "Staff " + id, role, Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions add(String name) throws Exception {
        return mvc.perform(json(post("/api/lead-sources"), "{\"name\":\"" + name + "\"}").with(as(admin, Role.ADMIN)));
    }

    private long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private int audits(String action, long id) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_type = 'LEAD_SOURCE' "
                + "and entity_id = ?", Integer.class, action, id);
    }

    @Test
    void listsSeededSourcesSortedForSales() throws Exception {
        mvc.perform(get("/api/lead-sources").with(as(rep, Role.SALES))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Facebook", "Phone call", "Referral", "Trade show",
                        "Walk-in", "Website")));
    }

    @Test
    void archivedHiddenUnlessAdminAsks() throws Exception {
        long id = id(add(tag + "Old").andExpect(status().isCreated()));
        mvc.perform(post("/api/lead-sources/" + id + "/archive").with(csrf(mvc)).with(as(admin, Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        assertThat(audits("LEAD_SOURCE_ARCHIVED", id)).isEqualTo(1);
        mvc.perform(get("/api/lead-sources").with(as(admin, Role.ADMIN)))
                .andExpect(jsonPath("$[*].name", not(hasItem(tag + "Old"))));
        mvc.perform(get("/api/lead-sources?includeArchived=true").with(as(rep, Role.SALES)))
                .andExpect(jsonPath("$[*].name", not(hasItem(tag + "Old"))));
        mvc.perform(get("/api/lead-sources?includeArchived=true").with(as(admin, Role.ADMIN)))
                .andExpect(jsonPath("$[*].name", hasItem(tag + "Old")));
        mvc.perform(post("/api/lead-sources/" + id + "/restore").with(csrf(mvc)).with(as(admin, Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        assertThat(audits("LEAD_SOURCE_RESTORED", id)).isEqualTo(1);
    }

    @Test
    void addIsAuditedAndTrimmed() throws Exception {
        long id = id(add("  " + tag + "Insta  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(tag + "Insta")));
        assertThat(audits("LEAD_SOURCE_ADDED", id)).isEqualTo(1);
    }

    @Test
    void duplicateNameIgnoringCaseIs409() throws Exception {
        add(" walk-in ").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_SOURCE_EXISTS"));
    }

    @Test
    void renameAuditedClashAndStaleRefused() throws Exception {
        long id = id(add(tag + "Insta"));
        mvc.perform(json(put("/api/lead-sources/" + id), "{\"name\":\"" + tag + "IG\",\"version\":0}")
                        .with(as(admin, Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(tag + "IG"));
        assertThat(audits("LEAD_SOURCE_RENAMED", id)).isEqualTo(1);
        mvc.perform(json(put("/api/lead-sources/" + id), "{\"name\":\"Referral\",\"version\":1}")
                        .with(as(admin, Role.ADMIN)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_SOURCE_EXISTS"));
        mvc.perform(json(put("/api/lead-sources/" + id), "{\"name\":\"" + tag + "X\",\"version\":0}")
                        .with(as(admin, Role.ADMIN)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void salesCannotWriteWarehouseCannotRead() throws Exception {
        mvc.perform(json(post("/api/lead-sources"), "{\"name\":\"" + tag + "X\"}").with(as(rep, Role.SALES)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lead-sources").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
