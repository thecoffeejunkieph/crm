package ph.thecoffeejunkie.crm.leads;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** Seeds lead data with JDBC. Everything carries {@link #tag} and is removed by {@link #cleanUp()}. */
class LeadsFixture {

    final String tag = "LD" + UUID.randomUUID().toString().substring(0, 6);
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;

    LeadsFixture(JdbcTemplate jdbc, MockMvc mvc) {
        this.jdbc = jdbc;
        this.mvc = mvc;
    }

    long user(String name, Role role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, ?, 'U', ?) "
                + "returning id", Long.class, tag + name + "@ld.it", tag + name, role.name());
    }

    static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "u" + id + "@ld.it",
                "Staff " + id, role, Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    long source(String name) {
        return jdbc.queryForObject("select id from lead_source where name = ?", Long.class, name);
    }

    long archivedSource() {
        return jdbc.queryForObject("insert into lead_source (name, archived_at) values (?, now()) returning id",
                Long.class, tag + "Old");
    }

    long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, phone, email, payment_terms, "
                + "assigned_rep_id) values ('INDIVIDUAL', ?, null, null, 'DUE_ON_RECEIPT', ?) returning id", Long.class,
                tag + name, repId);
    }

    /** A lead straight in the table; contact name is prefixed with the tag. */
    long lead(String name, Long repId, String status, String phone, String email) {
        return jdbc.queryForObject("insert into lead (contact_name, phone, email, assigned_rep_id, status, lost_reason, "
                + "created_by) values (?, ?, ?, ?, ?, ?, (select id from app_user order by id limit 1)) returning id",
                Long.class, tag + name, phone, email == null && phone == null ? tag + "@x.ph" : email, repId, status,
                "LOST".equals(status) ? "No budget" : null);
    }

    int audits(String action, long id) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_type = 'LEAD' "
                + "and entity_id = ?", Integer.class, action, id);
    }

    void cleanUp() {
        String customers = "select id from customer where display_name like '" + tag + "%'";
        jdbc.update("update customer set converted_from_lead_id = null where id in (" + customers + ")");
        jdbc.update("delete from lead where contact_name like ? or created_by in (select id from app_user where email "
                + "like ?)", tag + "%", tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from lead_source where name like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }
}
