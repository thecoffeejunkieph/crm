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
 * Asks why a customer declined a quote, the day after. The 30-day ceiling keeps the first scan after rollout from
 * flooding reps with old declines. Each decline event keys its own row, so a second decline is asked about too.
 */
@Component
class QuoteDeclinedRule implements AutoRule {

    private final JdbcTemplate jdbc;

    QuoteDeclinedRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.QUOTE_DECLINED;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select o.customer_id, o.id, o.rep_id, o.number, c.display_name, a.id "
                + "from audit_event a join sales_order o on a.entity_type = 'ORDER' and a.entity_id = o.id "
                + "join customer c on c.id = o.customer_id and c.archived_at is null "
                + "where a.action = 'QUOTE_DECLINED' and (a.at at time zone 'Asia/Manila')::date between ? and ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "QUOTE_DECLINED:order:" + rs.getLong(2) + ":declined:" + rs.getLong(6),
                        "Ask " + rs.getString(5) + " why they declined " + rs.getString(4), null),
                java.sql.Date.valueOf(today.minusDays(30)), java.sql.Date.valueOf(today.minusDays(1)));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Map.of();
    }
}
