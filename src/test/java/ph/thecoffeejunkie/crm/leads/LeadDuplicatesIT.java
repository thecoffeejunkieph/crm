package ph.thecoffeejunkie.crm.leads;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;


import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.leads.LeadsFixture.as;

@IntegrationTest
class LeadDuplicatesIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    LeadsFixture f;
    long rep;
    long other;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new LeadsFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions dupes(String query) throws Exception {
        return mvc.perform(get("/api/leads/duplicates?" + query).with(asRep)).andExpect(status().isOk());
    }

    private long customer(String name, Long repId, String phone, String email) {
        return jdbc.queryForObject("insert into customer (type, display_name, phone, email, payment_terms, "
                + "assigned_rep_id) values ('INDIVIDUAL', ?, ?, ?, 'DUE_ON_RECEIPT', ?) returning id", Long.class,
                f.tag + name, phone, email, repId);
    }

    @Test
    void emailMatchesIgnoringCase() throws Exception {
        long lead = f.lead("Ana", rep, "NEW", null, "ANA@" + f.tag + ".PH");
        dupes("email=ana@" + f.tag.toLowerCase() + ".ph")
                .andExpect(jsonPath("$.leads[*].id", containsInAnyOrder((int) lead)))
                .andExpect(jsonPath("$.leads[0].name").value(f.tag + "Ana"))
                .andExpect(jsonPath("$.hidden").value(0));
    }

    @Test
    void phoneMatchesAcrossFormats() throws Exception {
        String tail = String.format("%04d", new java.util.Random().nextInt(10_000));
        long cust = customer("Kape", rep, "0917-123-" + tail, null);
        String digits = "917123" + tail;
        mvc.perform(get("/api/leads/duplicates").param("phone", "+63 " + digits).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customers[*].id", containsInAnyOrder((int) cust)));
    }

    @Test
    void otherRepsMatchesAreCountedNotNamed() throws Exception {
        String email = "x@" + f.tag + ".ph";
        f.lead("Theirs", other, "NEW", null, email);
        customer("TheirCust", other, null, email);
        dupes("email=" + email).andExpect(jsonPath("$.leads", empty()))
                .andExpect(jsonPath("$.customers", empty()))
                .andExpect(jsonPath("$.hidden").value(2));
    }

    @Test
    void closedLeadsArchivedCustomersAndSelfAreSkipped() throws Exception {
        String email = "y@" + f.tag + ".ph";
        f.lead("Lost", rep, "LOST", null, email);
        f.lead("Won", rep, "CONVERTED", null, email);
        long self = f.lead("Self", rep, "NEW", null, email);
        long gone = customer("Gone", rep, null, email);
        jdbc.update("update customer set archived_at = now() where id = ?", gone);
        dupes("email=" + email + "&excludeId=" + self).andExpect(jsonPath("$.leads", empty()))
                .andExpect(jsonPath("$.customers", empty())).andExpect(jsonPath("$.hidden").value(0));
    }

    @Test
    void nothingAskedNothingFound() throws Exception {
        dupes("phone=&email=").andExpect(jsonPath("$.leads", empty())).andExpect(jsonPath("$.customers", empty()));
    }
}
