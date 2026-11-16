package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class WinBackRuleIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutoFollowUps engine;

    FollowUpsFixture f;
    long rep;
    long acme;
    LocalDate last;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
        last = f.newDay().plusDays(2);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = 'WIN_BACK' order by id", acme);
    }

    @Test
    void firesAfterSixtyDays() {
        long order = f.delivered(acme, rep, last);

        engine.run(last.plusDays(59));
        assertThat(rows()).isEmpty();
        engine.run(last.plusDays(60));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("title")).isEqualTo(f.tag + "Acme hasn't ordered in 60 days — check in");
        assertThat(r.get("auto_key")).isEqualTo("WIN_BACK:customer:" + acme + ":" + order);
        assertThat(r.get("order_id")).isNull();
        assertThat(r.get("assignee_id")).isEqualTo(rep);
    }

    @Test
    void notWithOpenOrder() {
        f.delivered(acme, rep, last);
        f.salesOrder(acme, rep, "CONFIRMED", FollowUpsFixture.at(last.plusDays(30)));

        engine.run(last.plusDays(60));

        assertThat(rows()).isEmpty();
    }

    @Test
    void newCycleAfterNextOrder() {
        long first = f.delivered(acme, rep, last);
        engine.run(last.plusDays(60));
        long second = f.delivered(acme, rep, last.plusDays(65));
        engine.run(last.plusDays(125));

        assertThat(rows()).extracting(r -> r.get("auto_key")).containsExactly(
                "WIN_BACK:customer:" + acme + ":" + first, "WIN_BACK:customer:" + acme + ":" + second);
    }

    @Test
    void confirmedOrderClosesItImmediately() {
        f.delivered(acme, rep, last);
        engine.run(last.plusDays(60));
        long reorder = f.salesOrder(acme, rep, "CONFIRMED", java.time.Instant.now());

        new org.springframework.transaction.support.TransactionTemplate(txm).executeWithoutResult(s ->
                events.publishEvent(new ph.thecoffeejunkie.crm.sales.OrderConfirmed(reorder, acme)));

        assertThat(rows().getFirst().get("closed_reason")).isEqualTo(f.tag + "Acme ordered " + f.orderNumber(reorder));
    }

    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.PlatformTransactionManager txm;
}
