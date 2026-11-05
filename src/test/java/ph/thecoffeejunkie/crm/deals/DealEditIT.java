package ph.thecoffeejunkie.crm.deals;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.deals.DealsFixture.as;

@IntegrationTest
class DealEditIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    DealsFixture f;
    long rep;
    long other;
    long acme;
    RequestPostProcessor asRep;

    @BeforeEach
    void seed() {
        f = new DealsFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        acme = f.customer("Acme", rep);
        asRep = as(rep, Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions edit(long id, String extra, long version) throws Exception {
        return mvc.perform(f.json(put("/api/deals/" + id), "{\"title\":\"" + f.tag + "Beans\"" + extra
                + ",\"version\":" + version + "}").with(asRep));
    }

    private ResultActions stage(RequestPostProcessor who, long id, String stage, String reason, long version)
            throws Exception {
        String r = reason == null ? "" : ",\"lostReason\":\"" + reason + "\"";
        return mvc.perform(f.json(post("/api/deals/" + id + "/stage"), "{\"stage\":\"" + stage + "\"" + r
                + ",\"version\":" + version + "}").with(who));
    }

    @Test
    void editIsAuditedAndStaleRefused() throws Exception {
        long id = f.deal(acme, rep, "Beans", "DISCOVERY", null, null);
        edit(id, ",\"value\":1200,\"notes\":\"Two branches\"", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(1200)).andExpect(jsonPath("$.notes").value("Two branches"));
        assertThat(f.audits("DEAL_UPDATED", id)).isEqualTo(1);
        edit(id, "", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void openStagesMoveFreely() throws Exception {
        long id = f.deal(acme, rep, "Beans", "DISCOVERY", null, null);
        stage(asRep, id, "NEGOTIATION", null, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("NEGOTIATION"));
        stage(asRep, id, "PROPOSAL", null, 1).andExpect(status().isOk());
        assertThat(f.audits("DEAL_STAGE_CHANGED", id)).isEqualTo(2);
    }

    @Test
    void lostNeedsReasonAndCanBeReopened() throws Exception {
        long id = f.deal(acme, rep, "Beans", "PROPOSAL", null, null);
        stage(asRep, id, "LOST", null, 0).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEAL_LOST_REASON"));
        stage(asRep, id, "LOST", "Chose a cheaper roaster", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").value("Chose a cheaper roaster"));
        edit(id, "", 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
        stage(asRep, id, "LOST", "again", 1).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
        stage(asRep, id, "PROPOSAL", null, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").isEmpty());
    }

    @Test
    void reopeningGoesToTheCustomersCurrentRep() throws Exception {
        long id = f.deal(acme, rep, "Beans", "LOST", null, null);
        jdbc.update("update customer set assigned_rep_id = ? where id = ?", other, acme);
        stage(asRep, id, "PROPOSAL", null, 0).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select stage from deal where id = ?", String.class, id)).isEqualTo("LOST");
        long admin = f.user("adm", Role.ADMIN);
        stage(as(admin, Role.ADMIN), id, "PROPOSAL", null, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.repId").value(other));
    }

    @Test
    void wonIsFinal() throws Exception {
        long id = f.deal(acme, rep, "Beans", "WON", null, null);
        edit(id, "", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
        stage(asRep, id, "DISCOVERY", null, 0).andExpect(jsonPath("$.code").value("DEAL_CLOSED"));
    }

    @Test
    void manualWonWithoutConfirmedOrderRefused() throws Exception {
        long id = f.deal(acme, rep, "Beans", "NEGOTIATION", null, null);
        stage(asRep, id, "WON", null, 0).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEAL_WON_NEEDS_ORDER"));
    }

    @Test
    void manualWonWithConfirmedOrder() throws Exception {
        long id = f.deal(acme, rep, "Beans", "NEGOTIATION", null, null);
        f.link(id, f.order(acme, "QUOTED"));
        long confirmed = f.order(acme, "CONFIRMED");
        f.link(id, confirmed);
        stage(asRep, id, "WON", null, 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("WON"))
                .andExpect(jsonPath("$.wonOrderId").value(confirmed))
                .andExpect(jsonPath("$.wonAt").isNotEmpty());
        assertThat(f.audits("DEAL_WON", id)).isEqualTo(1);
    }

    @Test
    void otherRepGets404() throws Exception {
        long id = f.deal(acme, rep, "Beans", "DISCOVERY", null, null);
        stage(as(other, Role.SALES), id, "PROPOSAL", null, 0).andExpect(status().isNotFound());
    }
}
