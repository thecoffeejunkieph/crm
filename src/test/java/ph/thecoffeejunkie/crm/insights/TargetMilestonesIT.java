package ph.thecoffeejunkie.crm.insights;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.sales.PaymentVerified;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/** Payments are seeded VERIFIED on VAT-exempt orders, so each one's credit equals its cash. Target is 1000.00. */
@IntegrationTest
class TargetMilestonesIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher events;

    InsightsFixture f;
    long admin;
    long rep;
    long order;
    final YearMonth nov = YearMonth.of(2026, 11);

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        order = f.order(f.customer("acme", rep), rep, "FULFILLED", "100000.00", "0.00", null);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from notification where recipient_user_id = ?", rep);
        jdbc.update("delete from sales_target where rep_id = ?", rep);
        f.cleanUp();
    }

    private void target(YearMonth m, String amount) {
        jdbc.update("insert into sales_target (rep_id, month, target_amount, commission_pct, updated_by) "
                + "values (?, ?, ?::numeric, 5, ?)", rep, m.atDay(1), amount, admin);
    }

    /** Seeds an earlier verified payment, so progress starts at `already`. */
    private void already(String cash) {
        f.payment(order, "VERIFIED", cash, "0.00", at(nov, 1));
    }

    private void verify(String cash, Instant at, Long repId) {
        long p = f.payment(order, "VERIFIED", cash, "0.00", at);
        tx.executeWithoutResult(s -> events.publishEvent(new PaymentVerified(order, p, repId, at)));
    }

    private List<String> titles() {
        return jdbc.queryForList("select title from notification where recipient_user_id = ? "
                + "and type = 'TARGET_MILESTONE' order by id", String.class, rep);
    }

    @Test
    void jumpFrom40To85SendsOnlyEighty() {
        target(nov, "1000.00");
        already("400.00");
        verify("450.00", at(nov, 2), rep);
        assertThat(titles()).containsExactly("You're at 80% of your November target");
        assertThat(jdbc.queryForObject("select entity_type || ':' || coalesce(entity_id::text, '') || ':' || body "
                + "from notification where recipient_user_id = ? and type = 'TARGET_MILESTONE'", String.class, rep))
                .isEqualTo("SALES_TARGET::850.00 of 1000.00");
    }

    @Test
    void from85To90SendsNothing() {
        target(nov, "1000.00");
        already("850.00");
        verify("50.00", at(nov, 2), rep);
        assertThat(titles()).isEmpty();
    }

    @Test
    void reachingHundredSendsTargetHit() {
        target(nov, "1000.00");
        already("950.00");
        verify("50.00", at(nov, 2), rep);
        assertThat(titles()).containsExactly("Target hit for November");
    }

    @Test
    void noTargetSendsNothing() {
        verify("900.00", at(nov, 2), rep);
        assertThat(titles()).isEmpty();
    }

    @Test
    void unassignedOrderSendsNothing() {
        target(nov, "1000.00");
        verify("900.00", at(nov, 2), null);
        assertThat(titles()).isEmpty();
    }

    @Test
    void manilaMonthBoundary() {
        YearMonth dec = YearMonth.of(2026, 12);
        target(dec, "1000.00");
        verify("600.00", Instant.parse("2026-11-30T16:30:00Z"), rep); // 00:30 on Dec 1 in Manila
        assertThat(titles()).containsExactly("You're at 50% of your December target");
    }

    @Test
    void crossedPicksTheHighestThreshold() {
        assertThat(TargetMilestones.crossed(40, 85)).isEqualTo(80);
        assertThat(TargetMilestones.crossed(85, 90)).isNull();
        assertThat(TargetMilestones.crossed(0, 50)).isEqualTo(50);
        assertThat(TargetMilestones.crossed(99, 250)).isEqualTo(100);
    }
}
