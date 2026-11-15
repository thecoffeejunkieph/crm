package ph.thecoffeejunkie.crm.deals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.deals.DealsFixture.as;

@IntegrationTest
class DealReassignIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    DealsFixture f;
    long admin;
    long rep;
    long next;
    long acme;

    @BeforeEach
    void seed() {
        f = new DealsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        next = f.user("nxt", Role.SALES);
        acme = f.customer("Acme", rep);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private void assign(Long repId) throws Exception {
        mvc.perform(f.json(put("/api/customers/" + acme + "/assignment"), "{\"repId\":" + repId + "}")
                .with(as(admin, Role.ADMIN))).andExpect(status().isOk());
    }

    private Long repOf(long deal) {
        return jdbc.queryForObject("select rep_id from deal where id = ?", Long.class, deal);
    }

    @Test
    void openDealsFollowTheCustomer() throws Exception {
        long open = f.deal(acme, rep, "Open", "PROPOSAL", null, null);
        long won = f.deal(acme, rep, "Won", "WON", null, null);
        long lost = f.deal(acme, rep, "Lost", "LOST", null, null);
        assign(next);
        assertThat(repOf(open)).isEqualTo(next);
        assertThat(repOf(won)).isEqualTo(rep);
        assertThat(repOf(lost)).isEqualTo(rep);
        assertThat(f.audits("DEAL_REASSIGNED", open)).isEqualTo(1);
        mvc.perform(get("/api/deals/" + open).with(as(rep, Role.SALES))).andExpect(status().isNotFound());
        mvc.perform(get("/api/deals/" + open).with(as(next, Role.SALES))).andExpect(status().isOk());
    }

    @Test
    void unassigningLeavesDealsWithTheOldRep() throws Exception {
        long open = f.deal(acme, rep, "Open", "DISCOVERY", null, null);
        assign(null);
        assertThat(repOf(open)).isEqualTo(rep);
    }
}
