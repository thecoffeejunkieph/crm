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

/** A quote has sat unanswered for the chase window since it was last sent. */
@Component
class QuoteChaseRule implements AutoRule {

    private final JdbcTemplate jdbc;

    QuoteChaseRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.QUOTE_CHASE;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select o.customer_id, o.id, o.rep_id, o.number, o.total, o.revision from sales_order o "
                + "join customer c on c.id = o.customer_id and c.archived_at is null "
                + "where o.status = 'QUOTED' and (select max(a.at at time zone 'Asia/Manila')::date from audit_event a "
                + "where a.entity_type = 'ORDER' and a.entity_id = o.id and a.action = 'QUOTE_SENT') <= ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "QUOTE_CHASE:order:" + rs.getLong(2) + ":" + rs.getInt(6),
                        "Follow up on quote " + rs.getString(4) + " (" + Quotes.money(rs.getBigDecimal(5)) + ")", null),
                java.sql.Date.valueOf(today.minusDays(s.quoteChaseDays())));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Quotes.toClose(jdbc, FollowUpRule.QUOTE_CHASE);
    }
}
