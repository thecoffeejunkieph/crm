package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.followups.AutoRule;
import ph.thecoffeejunkie.crm.followups.Candidate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;
import ph.thecoffeejunkie.crm.followups.RuleSettings;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An order still owes money past its due date. Refunds already show in the balance (total less settled), so the
 * balance is all there is to read. The 60-day ceiling keeps the first scan after rollout from flooding reps.
 */
@Component
class PaymentOverdueRule implements AutoRule {

    private final JdbcTemplate jdbc;

    PaymentOverdueRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.PAYMENT_OVERDUE;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select o.customer_id, o.id, o.rep_id, o.number, o.total - o.settled_amount, o.due_date "
                + "from sales_order o join customer c on c.id = o.customer_id and c.archived_at is null "
                + "where o.status in ('CONFIRMED', 'RELEASED', 'FULFILLED') and o.total - o.settled_amount > 0 and o.due_date < ? and o.due_date >= ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "PAYMENT_OVERDUE:order:" + rs.getLong(2),
                        "Collect " + Quotes.money(rs.getBigDecimal(5)) + " on " + rs.getString(4) + " ("
                                + ChronoUnit.DAYS.between(rs.getObject(6, LocalDate.class), today) + " days overdue)", null),
                java.sql.Date.valueOf(today), java.sql.Date.valueOf(today.minusDays(60)));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select f.id, o.status = 'CANCELLED' from follow_up f join sales_order o on o.id = f.order_id "
                + "where f.rule = 'PAYMENT_OVERDUE' and f.done_at is null "
                + "and (o.status = 'CANCELLED' or o.total - o.settled_amount <= 0)",
                rs -> {
                    out.put(rs.getLong(1), rs.getBoolean(2) ? "Order cancelled" : "Paid in full");
                });
        return out;
    }
}
