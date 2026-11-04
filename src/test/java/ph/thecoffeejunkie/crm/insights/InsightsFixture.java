package ph.thecoffeejunkie.crm.insights;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/**
 * Seeds insights data straight into the tables, so dates are exact. Everything carries {@link #tag} and is removed
 * by {@link #cleanUp()}. Company-wide figures are shared with other tests: assert them on a quiet month or as deltas.
 */
class InsightsFixture {

    final String tag = "IF" + UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;

    InsightsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', ?) returning id", Long.class, tag + name + "@if.it", tag + name, role.name());
    }

    static RequestPostProcessor as(long id, Role role) {
        return as(id, role, Set.of());
    }

    static RequestPostProcessor as(long id, Role role, Set<Long> warehouseIds) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@if.it",
                "Staff " + id, role, warehouseIds), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    void cleanUp() {
        String users = "select id from app_user where email like '" + tag + "%'";
        jdbc.update("delete from sales_target where rep_id in (" + users + ") or updated_by in (" + users + ")");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
