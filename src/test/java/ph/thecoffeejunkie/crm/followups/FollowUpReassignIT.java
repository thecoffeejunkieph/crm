package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.followups.FollowUpsFixture.as;

@IntegrationTest
class FollowUpReassignIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    FollowUpsFixture f;
    long admin;
    long oldRep;
    long newRep;
    long third;
    long acme;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        oldRep = f.user("old", Role.SALES);
        newRep = f.user("new", Role.SALES);
        third = f.user("thd", Role.SALES);
        acme = f.customer("acme", oldRep);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private void assign(Long repId) throws Exception {
        mvc.perform(f.json(put("/api/customers/" + acme + "/assignment"), "{\"repId\":" + repId + "}")
                .with(as(admin, Role.ADMIN))).andExpect(status().isOk());
    }

    private long assignee(long id) {
        return jdbc.queryForObject("select assignee_id from follow_up where id = ?", Long.class, id);
    }

    @Test
    void openOldRepRowsMove() throws Exception {
        long a = f.followUp(acme, null, oldRep, "a", f.today, null);
        long b = f.followUp(acme, null, oldRep, "b", f.today.plusDays(1), null);
        assign(newRep);
        assertThat(assignee(a)).isEqualTo(newRep);
        assertThat(assignee(b)).isEqualTo(newRep);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'FOLLOW_UP_REASSIGNED' "
                + "and entity_id in (?, ?)", Integer.class, a, b)).isEqualTo(2);
    }

    @Test
    void doneRowsStay() throws Exception {
        long done = f.followUp(acme, null, oldRep, "done", f.today, Instant.now());
        assign(newRep);
        assertThat(assignee(done)).isEqualTo(oldRep);
    }

    @Test
    void otherAssigneesStay() throws Exception {
        long theirs = f.followUp(acme, null, third, "theirs", f.today, null);
        assign(newRep);
        assertThat(assignee(theirs)).isEqualTo(third);
    }

    @Test
    void unassignLeavesRows() throws Exception {
        long a = f.followUp(acme, null, oldRep, "a", f.today, null);
        assign(null);
        assertThat(assignee(a)).isEqualTo(oldRep);
    }

    @Test
    void oldRepLosesAccess() throws Exception {
        long a = f.followUp(acme, null, oldRep, "a", f.today, null);
        assign(newRep);
        mvc.perform(post("/api/follow-ups/" + a + "/done").with(csrf(mvc)).with(as(oldRep, Role.SALES)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/follow-ups/" + a + "/done").with(csrf(mvc)).with(as(newRep, Role.SALES)))
                .andExpect(status().isOk());
    }
}
