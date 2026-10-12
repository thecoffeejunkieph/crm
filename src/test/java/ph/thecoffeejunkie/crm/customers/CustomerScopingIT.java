package ph.thecoffeejunkie.crm.customers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class CustomerScopingIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String tag = "SC" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from customer_contact where customer_id in (select id from customer where display_name like ?)", tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private long user(String name, String role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'A', 'U', ?) returning id", Long.class, tag + name + "@sc.it", role);
    }

    private long customer(String name, Long repId) {
        return jdbc.queryForObject("insert into customer (type, display_name, assigned_rep_id, notes) "
                + "values ('BUSINESS', ?, ?, 'secret') returning id", Long.class, tag + name, repId);
    }

    private static UsernamePasswordAuthenticationToken as(long id, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(id, "u" + id + "@sc.it", "A U", role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void salesSeesOnlyAssigned() throws Exception {
        long a = user("a", "SALES"), b = user("b", "SALES");
        customer("mine", a);
        customer("theirs", b);
        mvc.perform(get("/api/customers").param("q", tag).with(authentication(as(a, Role.SALES))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value(tag + "mine"));
    }

    @Test
    void otherRepsCustomerIs404() throws Exception {
        long a = user("a", "SALES"), b = user("b", "SALES");
        long theirs = customer("theirs", b);
        var me = authentication(as(a, Role.SALES));
        String base = "/api/customers/" + theirs;
        mvc.perform(get(base).with(me)).andExpect(status().isNotFound());
        mvc.perform(put(base).with(me).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"BUSINESS\",\"displayName\":\"x\",\"paymentTerms\":\"NET_30\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/history").with(me)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/contacts").with(me).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"n\",\"email\":\"" + tag + "@x.ph\",\"portalEnabled\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void warehouseGets403OnCustomers() throws Exception {
        long w = user("w", "WAREHOUSE");
        var me = authentication(as(w, Role.WAREHOUSE));
        mvc.perform(get("/api/customers").with(me)).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/lookup").param("q", "ab").with(me)).andExpect(status().isForbidden());
    }

    @Test
    void lookupSpansAllCustomersButMinimalFields() throws Exception {
        long a = user("a", "SALES"), b = user("b", "SALES");
        customer("theirs", b);
        mvc.perform(get("/api/customers/lookup").param("q", tag).with(authentication(as(a, Role.SALES))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value(tag + "theirs"))
                .andExpect(jsonPath("$[0].creditLimit").doesNotExist())
                .andExpect(jsonPath("$[0].notes").doesNotExist())
                .andExpect(jsonPath("$[0].contacts").doesNotExist());
    }

    @Test
    void listWithoutQWorksForAdminAndSales() throws Exception {
        long a = user("a", "SALES"), admin = user("adm", "ADMIN");
        customer("mine", a);
        customer("other", null);
        mvc.perform(get("/api/customers").with(authentication(as(a, Role.SALES))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/customers").param("size", "100").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.displayName=='" + tag + "other')]").exists());
    }

    @Test
    void adminSeesUnassigned() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = customer("free", null);
        mvc.perform(get("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedRepId").doesNotExist());
    }
}
