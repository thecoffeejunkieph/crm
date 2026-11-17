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
import ph.thecoffeejunkie.crm.sales.QuoteClosed;
import ph.thecoffeejunkie.crm.shared.Role;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class QuoteRulesIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutoFollowUps engine;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager txm;

    FollowUpsFixture f;
    long rep;
    long acme;
    LocalDate base;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        base = f.newDay().plusDays(10);
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    List<Map<String, Object>> rows(String rule) {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = ? order by id", acme, rule);
    }

    long quoted(String total, LocalDate validUntil) {
        long id = f.salesOrder(acme, rep, "QUOTED", null);
        jdbc.update("update sales_order set total = ?::numeric, quote_valid_until = ? where id = ?", total, validUntil, id);
        return id;
    }

    long audit(String action, long orderId, LocalDate day) {
        return jdbc.queryForObject("insert into audit_event (at, actor_type, action, entity_type, entity_id, summary) "
                + "values (?, 'SYSTEM', ?, 'ORDER', ?, 'x') returning id", Long.class,
                Timestamp.from(FollowUpsFixture.at(day)), action, orderId);
    }

    void publish(QuoteClosed e) {
        new TransactionTemplate(txm).executeWithoutResult(s -> events.publishEvent(e));
    }

    @Test
    void chaseFiresThreeDaysAfterSend() {
        long o = quoted("1500", null);
        long sent = audit("QUOTE_SENT", o, base);

        engine.run(base.plusDays(2));
        assertThat(rows("QUOTE_CHASE")).isEmpty();
        engine.run(base.plusDays(3));

        Map<String, Object> r = rows("QUOTE_CHASE").getFirst();
        assertThat(r.get("title")).isEqualTo("Follow up on quote " + f.orderNumber(o) + " (₱1,500.00)");
        assertThat(r.get("auto_key")).isEqualTo("QUOTE_CHASE:order:" + o + ":sent:" + sent);
        assertThat(r.get("assignee_id")).isEqualTo(rep);
    }

    @Test
    void expiringFiresTwoDaysBeforeValidUntil() {
        long o = quoted("100", base.plusDays(10));
        long sent = audit("QUOTE_SENT", o, base);

        engine.run(base.plusDays(7));
        assertThat(rows("QUOTE_EXPIRING")).isEmpty();
        engine.run(base.plusDays(8));

        Map<String, Object> r = rows("QUOTE_EXPIRING").getFirst();
        assertThat(r.get("title")).isEqualTo("Quote " + f.orderNumber(o) + " expires "
                + base.plusDays(10).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " — confirm or extend");
        assertThat(r.get("auto_key")).isEqualTo("QUOTE_EXPIRING:order:" + o + ":sent:" + sent);
    }

    @Test
    void declinedFiresNextDay() {
        long o = f.salesOrder(acme, rep, "DRAFT", null);
        long declined = audit("QUOTE_DECLINED", o, base);

        engine.run(base);
        assertThat(rows("QUOTE_DECLINED")).isEmpty();
        engine.run(base.plusDays(1));

        Map<String, Object> r = rows("QUOTE_DECLINED").getFirst();
        assertThat(r.get("title")).isEqualTo("Ask " + f.tag + "Acme why they declined " + f.orderNumber(o));
        assertThat(r.get("auto_key")).isEqualTo("QUOTE_DECLINED:order:" + o + ":declined:" + declined);
    }

    @Test
    void declinedRolloutLimit() {
        long o = f.salesOrder(acme, rep, "DRAFT", null);
        audit("QUOTE_DECLINED", o, base);

        engine.run(base.plusDays(31));
        assertThat(rows("QUOTE_DECLINED")).isEmpty();
        engine.run(base.plusDays(30));
        assertThat(rows("QUOTE_DECLINED")).hasSize(1);
    }

    @Test
    void acceptClosesChaseAndExpiring() {
        long o = quoted("100", base.plusDays(5));
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        assertThat(rows("QUOTE_CHASE")).hasSize(1);
        assertThat(rows("QUOTE_EXPIRING")).hasSize(1);

        publish(new QuoteClosed(o, "accepted"));

        for (String rule : List.of("QUOTE_CHASE", "QUOTE_EXPIRING")) {
            Map<String, Object> r = rows(rule).getFirst();
            assertThat(r.get("done_at")).isNotNull();
            assertThat(r.get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " accepted");
        }
    }

    /** What editing a QUOTED order and sending it again does: no revision bump, a new QUOTE_SENT audit. */
    @Test
    void reviseClosesOldChaseAndNewRevisionIsChasedAgain() {
        long o = quoted("100", null);
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        publish(new QuoteClosed(o, "revised")); // the edit sent it back to DRAFT
        assertThat(rows("QUOTE_CHASE").getFirst().get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " revised");

        jdbc.update("update sales_order set status = 'DRAFT' where id = ?", o);
        jdbc.update("update sales_order set status = 'QUOTED' where id = ?", o);
        long resent = audit("QUOTE_SENT", o, base.plusDays(4));
        engine.run(base.plusDays(6));
        assertThat(rows("QUOTE_CHASE")).hasSize(1);
        engine.run(base.plusDays(7));

        List<Map<String, Object>> all = rows("QUOTE_CHASE");
        assertThat(all).hasSize(2);
        assertThat(all.get(1).get("auto_key")).isEqualTo("QUOTE_CHASE:order:" + o + ":sent:" + resent);
        assertThat(all.get(1).get("done_at")).isNull();
    }

    @Test
    void resendFromQuotedClosesOldExpiringRow() {
        long o = quoted("100", base.plusDays(5));
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        assertThat(rows("QUOTE_EXPIRING")).hasSize(1);

        long resent = audit("QUOTE_SENT", o, base.plusDays(4));
        jdbc.update("update sales_order set quote_valid_until = ? where id = ?", base.plusDays(20), o);
        publish(new QuoteClosed(o, "resent"));

        Map<String, Object> old = rows("QUOTE_EXPIRING").getFirst();
        assertThat(old.get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " resent");
        engine.run(base.plusDays(18));
        List<Map<String, Object>> all = rows("QUOTE_EXPIRING");
        assertThat(all).hasSize(2);
        assertThat(all.get(1).get("auto_key")).isEqualTo("QUOTE_EXPIRING:order:" + o + ":sent:" + resent);
    }

    @Test
    void resendSafetyNetClosesStaleRowsWithoutAnEvent() {
        long o = quoted("100", null);
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        audit("QUOTE_SENT", o, base.plusDays(4));

        engine.run(base.plusDays(5));

        assertThat(rows("QUOTE_CHASE").getFirst().get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " resent");
    }

    @Test
    void secondDeclineGetsItsOwnFollowUp() {
        long o = f.salesOrder(acme, rep, "DRAFT", null);
        audit("QUOTE_DECLINED", o, base);
        engine.run(base.plusDays(1));
        long second = audit("QUOTE_DECLINED", o, base.plusDays(5));

        engine.run(base.plusDays(6));

        List<Map<String, Object>> all = rows("QUOTE_DECLINED");
        assertThat(all).hasSize(2);
        assertThat(all.get(1).get("auto_key")).isEqualTo("QUOTE_DECLINED:order:" + o + ":declined:" + second);
    }

    @Test
    void safetyNetClosesRowsOfAQuoteNoLongerQuoted() {
        long o = quoted("100", null);
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        jdbc.update("update sales_order set status = 'CONFIRMED' where id = ?", o);

        engine.run(base.plusDays(4));

        assertThat(rows("QUOTE_CHASE").getFirst().get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " closed");
    }

    @Test
    void expiredQuoteNotChased() {
        long o = quoted("100", base.plusDays(2));
        audit("QUOTE_SENT", o, base);

        engine.run(base.plusDays(3));

        assertThat(rows("QUOTE_CHASE")).isEmpty();
    }

    @Test
    void acceptedButPendingApprovalNotChased() {
        long o = quoted("100", null);
        jdbc.update("update sales_order set customer_accepted_at = now() where id = ?", o);
        audit("QUOTE_SENT", o, base);

        engine.run(base.plusDays(3));

        assertThat(rows("QUOTE_CHASE")).isEmpty();
    }

    @Test
    void safetyNetClosesChaseOnceAccepted() {
        long o = quoted("100", base.plusDays(20));
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        jdbc.update("update sales_order set customer_accepted_at = now() where id = ?", o); // no event

        engine.run(base.plusDays(4));

        assertThat(rows("QUOTE_CHASE").getFirst().get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " accepted");
    }

    @Test
    void malformedQuoteKeyNeverBreaksTheScan() {
        long o = quoted("100", null);
        audit("QUOTE_SENT", o, base);
        engine.run(base.plusDays(3));
        long odd = quoted("100", null);
        jdbc.update("insert into follow_up (customer_id, order_id, assignee_id, title, due_date, rule, auto_key) "
                + "values (?, ?, ?, 't', current_date, 'QUOTE_CHASE', ?)", acme, odd, rep, "QUOTE_CHASE:order:" + odd + ":bogus");
        audit("QUOTE_SENT", o, base.plusDays(4));

        engine.run(base.plusDays(5));

        assertThat(rows("QUOTE_CHASE").getFirst().get("closed_reason")).isEqualTo("Quote " + f.orderNumber(o) + " resent");
    }
}
