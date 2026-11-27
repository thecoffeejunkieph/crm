package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.followups.AutoRule;
import ph.thecoffeejunkie.crm.followups.Candidate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;
import ph.thecoffeejunkie.crm.followups.RuleSettings;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Asks how a customer's first delivered order went. The 30-day ceiling keeps the first scan after rollout from
 * flooding reps with long-past first orders.
 */
@Component
class FirstOrderRule implements AutoRule {

    private final JdbcTemplate jdbc;

    FirstOrderRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.FIRST_ORDER;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select customer_id, order_id, rep_id, number, display_name from ("
                + "select distinct on (so.customer_id) so.customer_id, so.id order_id, so.rep_id, so.number, "
                + "c.display_name, (d.delivered_at at time zone 'Asia/Manila')::date dd "
                + "from delivery_order d join sales_order so on so.id = d.sales_order_id "
                + "join customer c on c.id = so.customer_id where d.status = 'DELIVERED' and c.archived_at is null "
                + "order by so.customer_id, d.delivered_at) x where dd <= ? and dd >= ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "FIRST_ORDER:customer:" + rs.getLong(1),
                        "How did " + rs.getString(5) + " find their first order (" + rs.getString(4) + ")?", null),
                java.sql.Date.valueOf(today.minusDays(s.firstOrderDays())), java.sql.Date.valueOf(today.minusDays(30)));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Map.of();
    }
}
