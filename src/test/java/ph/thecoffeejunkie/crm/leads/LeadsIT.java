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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.leads.LeadsFixture.as;
import static ph.thecoffeejunkie.crm.leads.LeadsFixture.id;

@IntegrationTest
class LeadsIT {

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

    private ResultActions create(RequestPostProcessor who, String body) throws Exception {
        return mvc.perform(f.json(post("/api/leads"), body).with(who));
    }

    private String body(String extra) {
        return "{\"contactName\":\"" + f.tag + "Bea\",\"company\":\"" + f.tag + "Kape\",\"phone\":\"0917 555 0101\""
                + extra + "}";
    }

    @Test
    void repCreatesForThemselfAndItIsAudited() throws Exception {
        long id = id(create(asRep, body(",\"assignedRepId\":" + other + ",\"sourceId\":" + f.source("Facebook")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedRepId").value(rep))
                .andExpect(jsonPath("$.assignedRepName").value(f.tag + "rep U"))
                .andExpect(jsonPath("$.sourceName").value("Facebook"))
                .andExpect(jsonPath("$.status").value("NEW")));
        assertThat(f.audits("LEAD_CREATED", id)).isEqualTo(1);
    }

    @Test
    void adminCreatesUnassignedOrForARep() throws Exception {
        create(asAdmin, body("")).andExpect(status().isCreated()).andExpect(jsonPath("$.assignedRepId").isEmpty());
        create(asAdmin, body(",\"assignedRepId\":" + rep)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedRepId").value(rep));
        create(asAdmin, body(",\"assignedRepId\":" + wh)).andExpect(status().isBadRequest());
    }

    @Test
    void needsAWayToReachThem() throws Exception {
        create(asRep, "{\"contactName\":\"" + f.tag + "Bea\",\"phone\":\"  \"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_CONTACT_REQUIRED"));
        create(asRep, "{\"contactName\":\"" + f.tag + "Bea\",\"socialMedia\":\"fb.com/bea\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void archivedSourceRefused() throws Exception {
        create(asRep, body(",\"sourceId\":" + f.archivedSource())).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LEAD_SOURCE_ARCHIVED"));
    }

    @Test
    void repSeesOnlyOwn() throws Exception {
        long mine = f.lead("mine", rep, "NEW", "0917", null);
        long theirs = f.lead("theirs", other, "NEW", "0918", null);
        mvc.perform(get("/api/leads/" + theirs).with(asRep)).andExpect(status().isNotFound());
        mvc.perform(get("/api/leads/" + mine).with(asRep)).andExpect(status().isOk());
        mvc.perform(get("/api/leads?q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) mine)));
        mvc.perform(get("/api/leads?q=" + f.tag).with(asAdmin))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) mine, (int) theirs)));
        mvc.perform(get("/api/leads?q=" + f.tag + "&repId=" + other).with(asAdmin))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) theirs)));
    }

    @Test
    void defaultListIsOpenLeadsAndStatusFilters() throws Exception {
        long open = f.lead("open", rep, "CONTACTED", "0917", null);
        long lost = f.lead("lost", rep, "LOST", "0918", null);
        mvc.perform(get("/api/leads?q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) open)));
        mvc.perform(get("/api/leads?status=LOST&q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) lost)));
    }

    @Test
    void searchFindsByCompany() throws Exception {
        long id = id(create(asRep, body("")));
        mvc.perform(get("/api/leads?q=" + f.tag.toLowerCase() + "kape").with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) id)));
    }

    @Test
    void countsWithinScope() throws Exception {
        f.lead("a", rep, "NEW", "1", null);
        f.lead("b", rep, "CONTACTED", "2", null);
        f.lead("c", rep, "LOST", "3", null);
        f.lead("d", other, "NEW", "4", null);
        mvc.perform(get("/api/leads/counts").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.newLeads").value(1))
                .andExpect(jsonPath("$.contacted").value(1))
                .andExpect(jsonPath("$.qualified").value(0))
                .andExpect(jsonPath("$.lost").value(1))
                .andExpect(jsonPath("$.converted").value(0));
    }

    @Test
    void warehouseIsForbidden() throws Exception {
        mvc.perform(get("/api/leads").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
