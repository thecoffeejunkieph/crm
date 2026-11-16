package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.deals.DealStage;
import ph.thecoffeejunkie.crm.deals.DealStageChanged;
import ph.thecoffeejunkie.crm.shared.Role;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DealStalledRuleIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutoFollowUps engine;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager txm;

    FollowUpsFixture f;
    LocalDate base;
    long rep;
    long acme;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        base = f.newDay();
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
    }

    @AfterEach
    void cleanUp() {
        // audit_event is append-only; the rows we add key on ids that never recur
        jdbc.update("delete from deal where customer_id = ?", acme);
        f.cleanUp();
    }

    long deal(String stage, LocalDate created) {
        return jdbc.queryForObject("insert into deal (customer_id, rep_id, title, stage, created_by, created_at) "
                + "values (?, ?, 'Big fit-out', ?, ?, ?) returning id", Long.class, acme, rep, stage, rep,
                Timestamp.from(FollowUpsFixture.at(created)));
    }

    void stageChangedOn(long deal, LocalDate day) {
        jdbc.update("insert into audit_event (at, actor_type, action, entity_type, entity_id, summary) "
                + "values (?, 'SYSTEM', 'DEAL_STAGE_CHANGED', 'DEAL', ?, 'x')", Timestamp.from(FollowUpsFixture.at(day)), deal);
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = 'DEAL_STALLED' order by id", acme);
    }

    @Test
    void firesAfterFourteenDaysInStage() {
        long d = deal("PROPOSAL", base);

        engine.run(base.plusDays(13));
        assertThat(rows()).isEmpty();
        engine.run(base.plusDays(14));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("auto_key")).isEqualTo("DEAL_STALLED:deal:" + d + ":" + base);
        assertThat(r.get("title")).isEqualTo("Move deal \"Big fit-out\" forward (Proposal, 14 days in stage)");
        assertThat(r.get("assignee_id")).isEqualTo(rep);
        assertThat(r.get("order_id")).isNull();
    }

    @Test
    void noteEditDoesNotReset() {
        long d = deal("DISCOVERY", base);
        engine.run(base.plusDays(14));
        jdbc.update("update deal set notes = 'called', updated_at = now() where id = ?", d);

        engine.run(base.plusDays(15));

        assertThat(rows()).hasSize(1);
        assertThat(rows().getFirst().get("done_at")).isNull();
    }

    @Test
    void stageChangeClosesItAndRestartsTheClock() {
        long d = deal("DISCOVERY", base);
        engine.run(base.plusDays(14));
        stageChangedOn(d, base.plusDays(15));
        jdbc.update("update deal set stage = 'PROPOSAL' where id = ?", d);

        new TransactionTemplate(txm).executeWithoutResult(s ->
                events.publishEvent(new DealStageChanged(d, DealStage.PROPOSAL)));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("done_at")).isNotNull();
        assertThat(r.get("closed_reason")).isEqualTo("Deal moved to Proposal");
        engine.run(base.plusDays(28));
        assertThat(rows()).hasSize(1);
        engine.run(base.plusDays(29));
        assertThat(rows()).hasSize(2);
        assertThat(rows().get(1).get("auto_key")).isEqualTo("DEAL_STALLED:deal:" + d + ":" + base.plusDays(15));
    }

    @Test
    void safetyNetClosesAfterStageChangeOrWin() {
        long d = deal("DISCOVERY", base);
        engine.run(base.plusDays(14));
        stageChangedOn(d, base.plusDays(15));
        engine.run(base.plusDays(16));
        assertThat(rows().getFirst().get("closed_reason")).isEqualTo("Deal moved to Discovery");

        long w = deal("NEGOTIATION", base);
        jdbc.update("delete from follow_up where customer_id = ?", acme);
        engine.run(base.plusDays(60));
        jdbc.update("update deal set stage = 'WON' where id = ?", w);
        engine.run(base.plusDays(61));
        assertThat(jdbc.queryForObject("select closed_reason from follow_up where auto_key like ?", String.class,
                "DEAL_STALLED:deal:" + w + ":%")).isEqualTo("Deal moved to Won");
    }

    @Test
    void wonDealNotChased() {
        deal("WON", base);
        jdbc.update("update deal set stage = 'LOST', lost_reason = 'price' where id = ?", deal("PROPOSAL", base));

        engine.run(base.plusDays(60));

        assertThat(rows()).isEmpty();
    }

    @Test
    void malformedKeyNeverBreaksTheScan() {
        jdbc.update("insert into follow_up (customer_id, assignee_id, title, due_date, created_by, rule, auto_key) "
                + "values (?, ?, 'x', ?, ?, 'DEAL_STALLED', 'DEAL_STALLED:deal:abc')", acme, rep, base, rep);

        engine.run(base.plusDays(1));
    }
}
