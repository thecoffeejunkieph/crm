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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.leads.LeadsFixture.as;

@IntegrationTest
class LeadEditIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    LeadsFixture f;
    long admin;
    long rep;
    long other;
    long wh;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new LeadsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        wh = f.user("wh", Role.WAREHOUSE);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions edit(long id, String extra, long version) throws Exception {
        return mvc.perform(f.json(put("/api/leads/" + id), "{\"contactName\":\"" + f.tag + "Bea\",\"phone\":\"0917\""
                + extra + ",\"version\":" + version + "}").with(asRep));
    }

    private ResultActions setStatus(long id, String status, String reason, long version) throws Exception {
        String r = reason == null ? "" : ",\"lostReason\":\"" + reason + "\"";
        return mvc.perform(f.json(post("/api/leads/" + id + "/status"), "{\"status\":\"" + status + "\"" + r
                + ",\"version\":" + version + "}").with(asRep));
    }

    private ResultActions assign(RequestPostProcessor who, long id, long repId, long version) throws Exception {
        return mvc.perform(f.json(post("/api/leads/" + id + "/assign"), "{\"repId\":" + repId + ",\"version\":"
                + version + "}").with(who));
    }

    @Test
    void editIsAuditedAndStaleRefused() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        edit(id, ",\"notes\":\"likes beans\"", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("likes beans"));
        assertThat(f.audits("LEAD_UPDATED", id)).isEqualTo(1);
        edit(id, "", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void archivedSourceMayStayButNotBeChosen() throws Exception {
        long old = f.archivedSource();
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        jdbc.update("update lead set source_id = ? where id = ?", old, id);
        edit(id, ",\"sourceId\":" + old + ",\"notes\":\"x\"", 0).andExpect(status().isOk());
        long id2 = f.lead("Cy", rep, "NEW", "0918", null);
        edit(id2, ",\"sourceId\":" + old, 0).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_SOURCE_ARCHIVED"));
    }

    @Test
    void editCannotDropEveryWayToReachThem() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        mvc.perform(f.json(put("/api/leads/" + id), "{\"contactName\":\"" + f.tag + "Bea\",\"version\":0}")
                .with(asRep)).andExpect(jsonPath("$.code").value("LEAD_CONTACT_REQUIRED"));
    }

    @Test
    void openStatusesMoveFreelyAndAreAudited() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        setStatus(id, "QUALIFIED", null, 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QUALIFIED"));
        setStatus(id, "CONTACTED", null, 1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        assertThat(f.audits("LEAD_STATUS_CHANGED", id)).isEqualTo(2);
        setStatus(id, "NEW", null, 1).andExpect(status().isConflict());
    }

    @Test
    void lostNeedsAReasonAndReopenClearsIt() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        setStatus(id, "LOST", null, 0).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_LOST_REASON"));
        setStatus(id, "LOST", "  ", 0).andExpect(jsonPath("$.code").value("LEAD_LOST_REASON"));
        setStatus(id, "LOST", "No budget", 0).andExpect(status().isOk()).andExpect(jsonPath("$.lostReason").value("No budget"));
        setStatus(id, "NEW", null, 1).andExpect(status().isOk()).andExpect(jsonPath("$.lostReason").isEmpty());
    }

    @Test
    void cannotSetConvertedByHand() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        setStatus(id, "CONVERTED", null, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void convertedLeadIsFinal() throws Exception {
        long id = f.lead("Bea", rep, "CONVERTED", "0917", null);
        edit(id, "", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEAD_CONVERTED"));
        setStatus(id, "NEW", null, 0).andExpect(jsonPath("$.code").value("LEAD_CONVERTED"));
        assign(asAdmin, id, other, 0).andExpect(jsonPath("$.code").value("LEAD_CONVERTED"));
    }

    @Test
    void adminReassigns() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        assign(asAdmin, id, other, 0).andExpect(status().isOk()).andExpect(jsonPath("$.assignedRepId").value(other));
        assertThat(f.audits("LEAD_ASSIGNED", id)).isEqualTo(1);
        mvc.perform(get("/api/leads/" + id).with(asRep)).andExpect(status().isNotFound());
        mvc.perform(get("/api/leads/" + id).with(as(other, Role.SALES))).andExpect(status().isOk());
        assign(asAdmin, id, wh, 1).andExpect(status().isBadRequest());
    }

    @Test
    void salesCannotAssign() throws Exception {
        long id = f.lead("Bea", rep, "NEW", "0917", null);
        assign(asRep, id, other, 0).andExpect(status().isForbidden());
    }
}
