package ph.thecoffeejunkie.crm.customers;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class CustomerSourceIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    final String tag = "CS" + UUID.randomUUID().toString().substring(0, 8);
    long admin;
    long referral;
    long oldSource;

    @BeforeEach
    void seed() {
        admin = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) values (?, 'A', 'U', "
                + "'ADMIN') returning id", Long.class, tag + "@cs.it");
        referral = jdbc.queryForObject("select id from lead_source where name = 'Referral'", Long.class);
        oldSource = jdbc.queryForObject("insert into lead_source (name, archived_at) values (?, now()) returning id",
                Long.class, tag + "Old");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from lead_source where name like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private RequestPostProcessor asAdmin() {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(admin, "a@cs.it", "Admin",
                Role.ADMIN, Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.with(csrf(mvc)).with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions create(Object sourceId) throws Exception {
        return mvc.perform(json(post("/api/customers"), """
                {"type":"INDIVIDUAL","displayName":"%sAna","paymentTerms":"DUE_ON_RECEIPT","sourceId":%s}"""
                .formatted(tag, sourceId)));
    }

    @Test
    void createNamesTheSource() throws Exception {
        create(referral).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(referral))
                .andExpect(jsonPath("$.sourceName").value("Referral"))
                .andExpect(jsonPath("$.convertedFromLeadId").isEmpty());
    }

    @Test
    void archivedSourceRejectedOnCreate() throws Exception {
        create(oldSource).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_SOURCE_ARCHIVED"));
    }

    @Test
    void unknownSourceIs404() throws Exception {
        create(999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void keepingAnArchivedSourceStillSaves() throws Exception {
        long id = jdbc.queryForObject("insert into customer (type, display_name, payment_terms, source_id) values "
                + "('INDIVIDUAL', ?, 'DUE_ON_RECEIPT', ?) returning id", Long.class, tag + "Kept", oldSource);
        mvc.perform(json(put("/api/customers/" + id), """
                {"type":"INDIVIDUAL","displayName":"%sKept","paymentTerms":"DUE_ON_RECEIPT","sourceId":%d,
                 "notes":"called","version":0}""".formatted(tag, oldSource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("called"))
                .andExpect(jsonPath("$.sourceName").value(tag + "Old"));
    }
}
