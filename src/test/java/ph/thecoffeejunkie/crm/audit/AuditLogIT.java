package ph.thecoffeejunkie.crm.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuditLogIT {

    @Autowired AuditLog audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired MockMvc mvc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private int count(String entityType) {
        return jdbc.queryForObject("select count(*) from audit_event where entity_type = ?", Integer.class, entityType);
    }

    private static UsernamePasswordAuthenticationToken auth(Role role) {
        var p = new StaffPrincipal(7, "a@x.ph", "Ada Admin", role, Set.of());
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void recordOutsideTransactionFails() {
        assertThatThrownBy(() -> audit.record(AuditAction.CREATED, "T1", 1L, "s", null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void auditRolledBackWithBusinessChange() {
        assertThatThrownBy(() -> new TransactionTemplate(tm).executeWithoutResult(s -> {
            audit.record(AuditAction.CREATED, "T2", 1L, "s", null);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("T2")).isZero();
    }

    @Test
    void staffActorCaptured() {
        SecurityContextHolder.getContext().setAuthentication(auth(Role.ADMIN));
        new TransactionTemplate(tm).executeWithoutResult(s ->
                audit.record(AuditAction.UPDATED, "T3", 5L, "s", AuditChanges.diff().field("n", "a", "b")));
        var row = jdbc.queryForMap(
                "select actor_type, actor_id, actor_name, changes::text c from audit_event where entity_type = 'T3'");
        assertThat(row).containsEntry("actor_type", "USER").containsEntry("actor_id", 7L)
                .containsEntry("actor_name", "Ada Admin");
        assertThat((String) row.get("c")).contains("\"n\"");
    }

    @Test
    void activityEndpointAdminOnly() throws Exception {
        new TransactionTemplate(tm).executeWithoutResult(s ->
                audit.recordFor("SYSTEM", null, null, AuditAction.SETTING_CHANGED, "T4", 1L, "visible"));
        mvc.perform(get("/api/admin/activity").param("entityType", "T4").with(authentication(auth(Role.SALES))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/activity").param("entityType", "T4").with(authentication(auth(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].summary").value("visible"));
    }
}
