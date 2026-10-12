package ph.thecoffeejunkie.crm.customers;

import com.jayway.jsonpath.JsonPath;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class CustomersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String tag = "CU" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from customer_contact where customer_id in (select id from customer where display_name like ?)", tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
    }

    private long user(String name, String role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'A', 'U', ?) returning id", Long.class, tag + name + "@cu.it", role);
    }

    private static UsernamePasswordAuthenticationToken as(long id, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(id, "u" + id + "@cu.it", "A U", role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String json(String name, String phone, String creditLimit, Long version) {
        return "{\"type\":\"BUSINESS\",\"displayName\":\"%s\",\"phone\":\"%s\",\"paymentTerms\":\"NET_30\"%s%s}".formatted(
                tag + name, phone, creditLimit == null ? "" : ",\"creditLimit\":" + creditLimit,
                version == null ? "" : ",\"version\":" + version);
    }

    private ResultActions create(long uid, Role role, String body) throws Exception {
        return mvc.perform(post("/api/customers").with(authentication(as(uid, role))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createdId(long uid, Role role, String name) throws Exception {
        String res = create(uid, role, json(name, "111", null, null)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions addContact(long uid, Role role, long cid, String email) throws Exception {
        return mvc.perform(post("/api/customers/" + cid + "/contacts").with(authentication(as(uid, role)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cee\",\"email\":\"" + email + "\",\"portalEnabled\":true}"));
    }

    @Test
    void salesCreateAssignsSelf() throws Exception {
        long rep = user("rep", "SALES");
        create(rep, Role.SALES, json("self", "111", "5000", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedRepId").value(rep))
                .andExpect(jsonPath("$.creditLimit").doesNotExist()); // ignored on create for SALES
    }

    @Test
    void salesCannotChangeCreditLimit() throws Exception {
        long rep = user("rep", "SALES");
        long id = createdId(rep, Role.SALES, "cl");
        mvc.perform(put("/api/customers/" + id).with(authentication(as(rep, Role.SALES))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("cl", "111", "9999", 0L)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminReassignmentAudited() throws Exception {
        long admin = user("adm", "ADMIN"), rep = user("rep", "SALES");
        long id = createdId(admin, Role.ADMIN, "as");
        mvc.perform(put("/api/customers/" + id + "/assignment").with(authentication(as(admin, Role.ADMIN)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{\"repId\":" + rep + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedRepId").value(rep));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type='CUSTOMER' "
                + "and action='ASSIGNMENT_CHANGED' and entity_id=?", Integer.class, id)).isEqualTo(1);
        mvc.perform(put("/api/customers/" + id + "/assignment").with(authentication(as(admin, Role.ADMIN)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{\"repId\":" + admin + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateContactEmailRejected() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "dup");
        String email = tag.toLowerCase() + "@dup.ph";
        addContact(admin, Role.ADMIN, id, email).andExpect(status().isOk());
        addContact(admin, Role.ADMIN, id, email.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void historyShowsFieldChanges() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "hist");
        mvc.perform(put("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("hist", "222", null, 0L)))
                .andExpect(status().isOk());
        String body = mvc.perform(get("/api/customers/" + id + "/history").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<List<String>> phone = JsonPath.read(body, "$.content[?(@.action=='UPDATED')].changes.phone");
        assertThat(phone).containsExactly(List.of("111", "222"));
    }

    @Test
    void staleVersionOnConcurrentEdit() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "stale");
        var edit = put("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("stale", "222", null, 0L));
        mvc.perform(edit).andExpect(status().isOk());
        mvc.perform(edit).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void lookupNeedsTwoChars() throws Exception {
        long admin = user("adm", "ADMIN");
        mvc.perform(get("/api/customers/lookup").param("q", "a").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }
}
