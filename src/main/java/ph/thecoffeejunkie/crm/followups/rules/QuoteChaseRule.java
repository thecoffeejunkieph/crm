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
        return jdbc.query("select o.customer_id, o.id, o.rep_id, o.number, o.total, s.id from sales_order o "
                + "join customer c on c.id = o.customer_id and c.archived_at is null "
                + "join lateral (" + Quotes.LATEST_SENT + ") s on true where o.status = 'QUOTED' and s.d <= ? "
                + "and o.customer_accepted_at is null and (o.quote_valid_until is null or o.quote_valid_until >= ?)",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "QUOTE_CHASE:order:" + rs.getLong(2) + ":sent:" + rs.getLong(6),
                        "Follow up on quote " + rs.getString(4) + " (" + Quotes.money(rs.getBigDecimal(5)) + ")", null),
                java.sql.Date.valueOf(today.minusDays(s.quoteChaseDays())), java.sql.Date.valueOf(today));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Quotes.toClose(jdbc, FollowUpRule.QUOTE_CHASE);
    }
}
