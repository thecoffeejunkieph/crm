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

/** A quote is within the warning window of its valid-until date. */
@Component
class QuoteExpiringRule implements AutoRule {

    private final JdbcTemplate jdbc;

    QuoteExpiringRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.QUOTE_EXPIRING;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select o.customer_id, o.id, o.rep_id, o.number, o.quote_valid_until, o.revision "
                + "from sales_order o join customer c on c.id = o.customer_id and c.archived_at is null "
                + "where o.status = 'QUOTED' and o.quote_valid_until >= ? and o.quote_valid_until - ? <= ?",
                (rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3),
                        "QUOTE_EXPIRING:order:" + rs.getLong(2) + ":" + rs.getInt(6),
                        "Quote " + rs.getString(4) + " expires "
                                + rs.getObject(5, LocalDate.class).format(Quotes.DAY_MONTH) + " — confirm or extend", null),
                java.sql.Date.valueOf(today), s.quoteExpiringDaysBefore(), java.sql.Date.valueOf(today));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Quotes.toClose(jdbc, FollowUpRule.QUOTE_EXPIRING);
    }
}
