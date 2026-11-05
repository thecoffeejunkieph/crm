package ph.thecoffeejunkie.crm.leads;

import com.jayway.jsonpath.JsonPath;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.leads.LeadsFixture.as;

@IntegrationTest
class LeadConvertIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    LeadsFixture f;
    long admin;
    long rep;
    long other;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new LeadsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private String newCustomer(String name, String extra) {
        return "{\"customer\":{\"type\":\"INDIVIDUAL\",\"displayName\":\"" + f.tag + name
                + "\",\"paymentTerms\":\"DUE_ON_RECEIPT\"" + extra + "},\"version\":0}";
    }

    private ResultActions convert(RequestPostProcessor who, long id, String body, String key) throws Exception {
        return mvc.perform(f.json(post("/api/leads/" + id + "/convert"), body).header("Idempotency-Key", key).with(who));
    }

    private ResultActions convert(RequestPostProcessor who, long id, String body) throws Exception {
        return convert(who, id, body, UUID.randomUUID().toString());
    }

    private int customersNamed(String name) {
        return jdbc.queryForObject("select count(*) from customer where display_name = ?", Integer.class, f.tag + name);
    }

    private String statusOf(long lead) {
        return jdbc.queryForObject("select status from lead where id = ?", String.class, lead);
    }

    @Test
    void repConvertsIntoANewCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "QUALIFIED", "0917", null);
        long fb = f.source("Facebook");
        jdbc.update("update lead set source_id = ? where id = ?", fb, lead);
        String body = convert(asRep, lead, newCustomer("Kape", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.customerName").value(f.tag + "Kape"))
                .andExpect(jsonPath("$.convertedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long customer = ((Number) JsonPath.read(body, "$.customerId")).longValue();
        mvc.perform(get("/api/customers/" + customer).with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedRepId").value(rep))
                .andExpect(jsonPath("$.sourceId").value(fb))
                .andExpect(jsonPath("$.convertedFromLeadId").value(lead));
        assertThat(f.audits("LEAD_CONVERTED", lead)).isEqualTo(1);
    }

    @Test
    void customerSourceInRequestWins() throws Exception {
        long lead = f.lead("Bea", rep, "NEW", "0917", null);
        jdbc.update("update lead set source_id = ? where id = ?", f.source("Facebook"), lead);
        long referral = f.source("Referral");
        String body = convert(asRep, lead, newCustomer("Kape", ",\"sourceId\":" + referral)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long customer = ((Number) JsonPath.read(body, "$.customerId")).longValue();
        assertThat(jdbc.queryForObject("select source_id from customer where id = ?", Long.class, customer))
                .isEqualTo(referral);
    }

    @Test
    void leadsArchivedSourceCarriesOverToTheCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "QUALIFIED", "0917", null);
        long old = f.archivedSource();
        jdbc.update("update lead set source_id = ? where id = ?", old, lead);
        String body = convert(asRep, lead, newCustomer("Kape", "")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long customer = ((Number) JsonPath.read(body, "$.customerId")).longValue();
        assertThat(jdbc.queryForObject("select source_id from customer where id = ?", Long.class, customer)).isEqualTo(old);
        long second = f.lead("Cy", rep, "QUALIFIED", "0918", null);
        jdbc.update("update lead set source_id = ? where id = ?", old, second);
        convert(asRep, second, newCustomer("Kape2", ",\"sourceId\":" + old)).andExpect(status().isOk());
    }

    @Test
    void adminConvertsUnassignedLead() throws Exception {
        long lead = f.lead("Bea", null, "NEW", "0917", null);
        convert(asAdmin, lead, newCustomer("Kape", "")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select assigned_rep_id from customer where display_name = ?", Long.class,
                f.tag + "Kape")).isNull();
    }

    @Test
    void linksAnExistingCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "CONTACTED", "0917", null);
        long cust = f.customer("Acme", rep);
        convert(asRep, lead, "{\"customerId\":" + cust + ",\"version\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED")).andExpect(jsonPath("$.customerId").value(cust));
        assertThat(jdbc.queryForObject("select converted_from_lead_id from customer where id = ?", Long.class, cust))
                .isEqualTo(lead);
        long second = f.lead("Cy", rep, "NEW", "0918", null);
        convert(asRep, second, "{\"customerId\":" + cust + ",\"version\":0}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select converted_from_lead_id from customer where id = ?", Long.class, cust))
                .isEqualTo(lead);
    }

    @Test
    void cannotLinkAnotherRepsOrAnArchivedCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "QUALIFIED", "0917", null);
        long theirs = f.customer("Theirs", other);
        convert(asRep, lead, "{\"customerId\":" + theirs + ",\"version\":0}").andExpect(status().isNotFound());
        long gone = f.customer("Gone", rep);
        jdbc.update("update customer set archived_at = now() where id = ?", gone);
        convert(asRep, lead, "{\"customerId\":" + gone + ",\"version\":0}").andExpect(status().isNotFound());
        assertThat(statusOf(lead)).isEqualTo("QUALIFIED");
        assertThat(jdbc.queryForObject("select converted_from_lead_id from customer where id = ?", Long.class, theirs))
                .isNull();
    }

    @Test
    void failedCustomerLeavesLeadOpenAndNoCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "QUALIFIED", "0917", null);
        convert(asRep, lead, newCustomer("Kape", ",\"sourceId\":" + f.archivedSource()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_SOURCE_ARCHIVED"));
        convert(asRep, lead, "{\"customer\":{\"type\":\"INDIVIDUAL\",\"displayName\":\" \",\"paymentTerms\":\"DUE_ON_RECEIPT\"},\"version\":0}")
                .andExpect(status().isBadRequest());
        assertThat(statusOf(lead)).isEqualTo("QUALIFIED");
        assertThat(customersNamed("Kape")).isZero();
    }

    @Test
    void lostLeadMustBeReopenedFirst() throws Exception {
        long lead = f.lead("Bea", rep, "LOST", "0917", null);
        convert(asRep, lead, newCustomer("Kape", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_LOST"));
    }

    @Test
    void doubleSubmitMakesOneCustomer() throws Exception {
        long lead = f.lead("Bea", rep, "NEW", "0917", null);
        String key = UUID.randomUUID().toString();
        String first = convert(asRep, lead, newCustomer("Kape", ""), key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String replay = convert(asRep, lead, newCustomer("Kape", ""), key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        convert(asRep, lead, newCustomer("Kape", "")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEAD_CONVERTED"));
        assertThat(customersNamed("Kape")).isEqualTo(1);
    }

    @Test
    void exactlyOneTargetAndCurrentVersion() throws Exception {
        long lead = f.lead("Bea", rep, "NEW", "0917", null);
        long cust = f.customer("Acme", rep);
        convert(asRep, lead, "{\"version\":0}").andExpect(status().isBadRequest());
        String both = newCustomer("Kape", "").replace("\"version\":0", "\"customerId\":" + cust + ",\"version\":0");
        convert(asRep, lead, both).andExpect(status().isBadRequest());
        convert(asRep, lead, "{\"customerId\":" + cust + ",\"version\":3}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }
}
