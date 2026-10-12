package ph.thecoffeejunkie.crm.warehouses;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class WarehousesIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    // Committed rows would trip BaselineSchemaIT's default-warehouse check, so remove everything we create.
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from warehouse where code like 'WT7-%'");
    }

    private static UsernamePasswordAuthenticationToken auth(Role role) {
        var p = new StaffPrincipal(1L, "a@x.ph", "Ann Admin", role, Set.of());
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static String body(String code, boolean isDefault, Long version) {
        return "{\"code\":\"%s\",\"name\":\"Main\",\"address\":\"1 Road\",\"isDefault\":%s%s}".formatted(
                code, isDefault, version == null ? "" : ",\"version\":" + version);
    }

    private ResultActions create(String code, boolean isDefault) throws Exception {
        return mvc.perform(post("/api/warehouses").with(authentication(auth(Role.ADMIN))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(code, isDefault, null)));
    }

    private long idOf(String code) {
        return jdbc.queryForObject("select id from warehouse where code = ?", Long.class, code);
    }

    @Test
    void adminCreatesAndListsWarehouse() throws Exception {
        create("WT7-A", false).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WT7-A"))
                .andExpect(jsonPath("$.isDefault").value(false));
        mvc.perform(get("/api/warehouses").with(authentication(auth(Role.WAREHOUSE))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code=='WT7-A')]").exists());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type='WAREHOUSE' "
                + "and action='CREATED' and entity_id = ?", Integer.class, idOf("WT7-A"))).isEqualTo(1);
    }

    @Test
    void duplicateCodeIgnoringCaseRejected() throws Exception {
        create("WT7-B", false).andExpect(status().isOk());
        create("wt7-b", false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void newDefaultReplacesOld() throws Exception {
        create("WT7-C", true).andExpect(status().isOk());
        create("WT7-D", true).andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true));
        assertThat(jdbc.queryForObject("select is_default from warehouse where code='WT7-C'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select is_default from warehouse where code='WT7-D'", Boolean.class)).isTrue();
    }

    @Test
    void cannotArchiveDefault() throws Exception {
        create("WT7-E", true).andExpect(status().isOk());
        mvc.perform(post("/api/warehouses/" + idOf("WT7-E") + "/archive")
                        .with(authentication(auth(Role.ADMIN))).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Choose another default warehouse first."));
    }

    @Test
    void staleVersionRejected() throws Exception {
        create("WT7-F", false).andExpect(status().isOk());
        long id = idOf("WT7-F");
        mvc.perform(put("/api/warehouses/" + id).with(authentication(auth(Role.ADMIN))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("WT7-F", false, 99L)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(put("/api/warehouses/" + id).with(authentication(auth(Role.ADMIN))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("WT7-F", false, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/warehouses/" + id).with(authentication(auth(Role.ADMIN))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("WT7-F", false, 0L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void salesCanListButNotCreate() throws Exception {
        mvc.perform(get("/api/warehouses").with(authentication(auth(Role.SALES)))).andExpect(status().isOk());
        mvc.perform(post("/api/warehouses").with(authentication(auth(Role.SALES))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body("WT7-G", false, null)))
                .andExpect(status().isForbidden());
    }
}
