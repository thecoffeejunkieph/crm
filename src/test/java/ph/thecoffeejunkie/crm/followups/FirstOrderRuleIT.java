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
class FirstOrderRuleIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutoFollowUps engine;

    FollowUpsFixture f;
    long rep;
    long acme;
    LocalDate day;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
        day = f.newDay().plusDays(50);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = 'FIRST_ORDER'", acme);
    }

    @Test
    void firesThreeDaysAfterFirstDelivery() {
        long order = f.delivered(acme, rep, day);

        engine.run(day.plusDays(2));
        assertThat(rows()).isEmpty();
        engine.run(day.plusDays(3));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("title")).isEqualTo("How did " + f.tag + "Acme find their first order ("
                + f.orderNumber(order) + ")?");
        assertThat(r.get("auto_key")).isEqualTo("FIRST_ORDER:customer:" + acme);
        assertThat(r.get("order_id")).isEqualTo(order);
        assertThat(r.get("assignee_id")).isEqualTo(rep);
    }

    @Test
    void notForSecondOrder() {
        f.delivered(acme, rep, day.minusDays(40));
        f.delivered(acme, rep, day);

        engine.run(day.plusDays(3));

        assertThat(rows()).isEmpty();
    }

    @Test
    void rolloutIgnoresFirstDeliveriesOlderThanThirtyDays() {
        f.delivered(acme, rep, day);

        engine.run(day.plusDays(31));

        assertThat(rows()).isEmpty();
    }

    @Test
    void dayThirtyIsStillIncluded() {
        f.delivered(acme, rep, day);

        engine.run(day.plusDays(30));

        assertThat(rows()).hasSize(1);
    }
}
