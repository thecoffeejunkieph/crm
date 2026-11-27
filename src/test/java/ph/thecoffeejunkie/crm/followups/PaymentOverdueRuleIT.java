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
import ph.thecoffeejunkie.crm.sales.PaymentVerified;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PaymentOverdueRuleIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutoFollowUps engine;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager txm;

    FollowUpsFixture f;
    long rep;
    long acme;
    LocalDate due;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        due = f.newDay().plusDays(10);
        rep = f.user("rep", Role.SALES);
        acme = f.customer("Acme", rep);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where customer_id = ? and rule = 'PAYMENT_OVERDUE'", acme);
    }

    long owing(String total, String settled) {
        long id = f.salesOrder(acme, rep, "CONFIRMED", null);
        jdbc.update("update sales_order set total = ?::numeric, settled_amount = ?::numeric, due_date = ? where id = ?",
                total, settled, due, id);
        return id;
    }

    @Test
    void firesWhenOverdue() {
        long o = owing("500", "0");

        engine.run(due);
        assertThat(rows()).isEmpty();
        engine.run(due.plusDays(1));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("auto_key")).isEqualTo("PAYMENT_OVERDUE:order:" + o);
        assertThat(r.get("assignee_id")).isEqualTo(rep);
    }

    @Test
    void titleUsesBalance() {
        long o = owing("10000", "4000");

        engine.run(due.plusDays(5));

        assertThat(rows().getFirst().get("title"))
                .isEqualTo("Collect ₱6,000.00 on " + f.orderNumber(o) + " (5 days overdue)");
    }

    @Test
    void paymentInFullClosesIt() {
        long o = owing("500", "0");
        engine.run(due.plusDays(1));
        jdbc.update("update sales_order set settled_amount = total where id = ?", o);

        new TransactionTemplate(txm).executeWithoutResult(s ->
                events.publishEvent(new PaymentVerified(o, 1L, rep, Instant.now())));

        Map<String, Object> r = rows().getFirst();
        assertThat(r.get("done_at")).isNotNull();
        assertThat(r.get("closed_reason")).isEqualTo("Paid in full");
    }

    @Test
    void safetyNetClosesCancelledOrder() {
        long o = owing("500", "0");
        engine.run(due.plusDays(1));
        jdbc.update("update sales_order set status = 'CANCELLED' where id = ?", o);

        engine.run(due.plusDays(2));

        assertThat(rows().getFirst().get("closed_reason")).isEqualTo("Order cancelled");
    }

    @Test
    void paymentOverdueIgnoresUnconfirmedOrders() {
        for (String status : List.of("DRAFT", "QUOTED", "CANCELLED")) {
            long id = f.salesOrder(acme, rep, status, null);
            jdbc.update("update sales_order set total = 500, due_date = ? where id = ?", due, id);
        }

        engine.run(due.plusDays(5));

        assertThat(rows()).isEmpty();
    }

    @Test
    void rolloutLimitSixtyDays() {
        owing("500", "0");

        engine.run(due.plusDays(61));
        assertThat(rows()).isEmpty();
        engine.run(due.plusDays(60));
        assertThat(rows()).hasSize(1);
    }
}
