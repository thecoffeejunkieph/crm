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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static ph.thecoffeejunkie.crm.followups.FollowUpsFixture.as;

@IntegrationTest
class RestockRuleIT {

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
        last = f.newDay().plusDays(50);
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = 'RESTOCK'", acme);
    }

    @Test
    void firesAtLearnedRhythm() {
        f.delivered(acme, rep, last.minusDays(40));
        f.delivered(acme, rep, last.minusDays(20));
        f.delivered(acme, rep, last);

        engine.run(last.plusDays(15)); // 15 < 16 days: too early
        assertThat(rows()).isEmpty();
        engine.run(last.plusDays(16));
        assertThat(rows()).hasSize(1);
    }

    @Test
    void newCustomerUsesFallback() {
        f.delivered(acme, rep, last);

        engine.run(last.plusDays(14));
        assertThat(rows()).isEmpty();
        engine.run(last.plusDays(15));
        assertThat(rows()).hasSize(1);
    }

    @Test
    void titleAndAssignee() {
        long order = f.delivered(acme, rep, last);

        engine.run(last.plusDays(15));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("title")).isEqualTo("Check if " + f.tag + "Acme still has stock — last order "
                + f.orderNumber(order) + ", 15 days ago");
        assertThat(r.get("assignee_id")).isEqualTo(rep);
        assertThat(r.get("order_id")).isEqualTo(order);
        assertThat(r.get("auto_key")).isEqualTo("RESTOCK:order:" + order);
    }

    @Test
    void notWhenCustomerAlreadyReordered() {
        f.delivered(acme, rep, last);
        f.salesOrder(acme, rep, "CONFIRMED", FollowUpsFixture.at(last.plusDays(5)));

        engine.run(last.plusDays(20));

        assertThat(rows()).isEmpty();
    }

    @Test
    void confirmedOrderClosesItImmediately() {
        f.delivered(acme, rep, last);
        engine.run(last.plusDays(15));
        long reorder = f.salesOrder(acme, rep, "CONFIRMED", java.time.Instant.now());

        new org.springframework.transaction.support.TransactionTemplate(txm).executeWithoutResult(s ->
                events.publishEvent(new ph.thecoffeejunkie.crm.sales.OrderConfirmed(reorder, acme)));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("done_at")).isNotNull();
        assertThat(r.get("closed_reason")).isEqualTo(f.tag + "Acme ordered " + f.orderNumber(reorder));
    }

    @Test
    void restockStopsAtWinBackAge() {
        f.delivered(acme, rep, last);

        engine.run(last.plusDays(60));

        assertThat(rows()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from follow_up where customer_id = ? and rule = 'WIN_BACK'",
                Integer.class, acme)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from follow_up where customer_id = ?", Integer.class, acme))
                .isEqualTo(1);
    }

    @Test
    void notWhileAnOrderIsInFlight() {
        f.delivered(acme, rep, last);
        f.salesOrder(acme, rep, "CONFIRMED", FollowUpsFixture.at(last.minusDays(1)));

        engine.run(last.plusDays(20));

        assertThat(rows()).isEmpty();
    }

    @Test
    void safetyNetClosesRowsOfAReorderedCustomer() {
        java.sql.Timestamp past = java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(10 * 86400));
        for (String rule : List.of("RESTOCK", "WIN_BACK")) {
            jdbc.update("insert into follow_up (customer_id, assignee_id, title, due_date, created_by, rule, auto_key, "
                    + "created_at, updated_at) values (?, ?, 't', current_date, null, ?, ?, ?, ?)", acme, rep, rule,
                    f.tag + ":" + rule, past, past);
        }
        long reorder = f.salesOrder(acme, rep, "CONFIRMED", java.time.Instant.now());

        engine.run(f.newDay());

        assertThat(jdbc.queryForList("select closed_reason from follow_up where customer_id = ?", String.class, acme))
                .hasSize(2).allSatisfy(r -> assertThat(r).isEqualTo(f.tag + "Acme ordered " + f.orderNumber(reorder)));
    }

    @Test
    void reassignmentMovesAutoFollowUp() throws Exception {
        long admin = f.user("adm", Role.ADMIN);
        long other = f.user("other", Role.SALES);
        f.delivered(acme, rep, last);
        engine.run(last.plusDays(15));

        mvc.perform(f.json(put("/api/customers/" + acme + "/assignment"), "{\"repId\":" + other + "}")
                .with(as(admin, Role.ADMIN))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertThat(rows().getFirst().get("assignee_id")).isEqualTo(other);
    }

    @Test
    void restockClosedWhenWinBackTakesOver() {
        f.delivered(acme, rep, last);
        engine.run(last.plusDays(15));
        assertThat(rows()).hasSize(1);

        engine.run(last.plusDays(60));

        assertThat(rows().getFirst().get("closed_reason")).isEqualTo("Handed to win-back");
        assertThat(jdbc.queryForList("select rule from follow_up where customer_id = ? and done_at is null and rule in ('RESTOCK', 'WIN_BACK')",
                String.class, acme)).containsExactly("WIN_BACK");
    }

    @Test
    void cancelledReorderDoesNotCloseRestock() {
        f.delivered(acme, rep, last);
        f.salesOrder(acme, rep, "CANCELLED", FollowUpsFixture.at(last.plusDays(5))); // confirmed, then cancelled
        engine.run(last.plusDays(15));
        assertThat(rows()).hasSize(1);
        f.salesOrder(acme, rep, "CANCELLED", java.time.Instant.now());

        engine.run(last.plusDays(16));

        assertThat(rows().getFirst().get("done_at")).isNull();
    }

    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.PlatformTransactionManager txm;
}
