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

/** An open deal has sat in its stage for the stalled window. Notes and value edits do not restart the clock. */
@Component
class DealStalledRule implements AutoRule {

    /** The Manila date the deal entered its stage: its newest DEAL_STAGE_CHANGED audit, else its creation. */
    private static final String LAST_CHANGE = "coalesce((select (a.at at time zone 'Asia/Manila')::date from audit_event a "
            + "where a.entity_type = 'DEAL' and a.entity_id = d.id and a.action = 'DEAL_STAGE_CHANGED' "
            + "order by a.at desc, a.id desc limit 1), (d.created_at at time zone 'Asia/Manila')::date)";

    private final JdbcTemplate jdbc;

    DealStalledRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.DEAL_STALLED;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select d.customer_id, d.id, d.rep_id, d.title, initcap(d.stage), x.d from deal d "
                + "join customer c on c.id = d.customer_id and c.archived_at is null "
                + "cross join lateral (select " + LAST_CHANGE + " d) x "
                + "where d.stage in ('DISCOVERY', 'PROPOSAL', 'NEGOTIATION') and x.d <= ?",
                (rs, i) -> {
                    LocalDate since = rs.getObject(6, LocalDate.class);
                    return new Candidate(rs.getLong(1), null, (Long) rs.getObject(3),
                            "DEAL_STALLED:deal:" + rs.getLong(2) + ":" + since,
                            "Move deal \"" + rs.getString(4) + "\" forward (" + rs.getString(5) + ", "
                                    + ChronoUnit.DAYS.between(since, today) + " days in stage)", null);
                }, java.sql.Date.valueOf(today.minusDays(s.dealStalledDays())));
    }

    /** Matched on the key's text, never parsed, so a malformed key cannot throw. */
    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select f.id, initcap(d.stage) from follow_up f join deal d "
                + "on starts_with(f.auto_key, 'DEAL_STALLED:deal:' || d.id || ':') "
                + "cross join lateral (select " + LAST_CHANGE + " d) x where f.rule = 'DEAL_STALLED' and f.done_at is null "
                + "and (d.stage in ('WON', 'LOST') or f.auto_key <> 'DEAL_STALLED:deal:' || d.id || ':' || x.d)",
                rs -> {
                    out.put(rs.getLong(1), "Deal moved to " + rs.getString(2));
                });
        return out;
    }
}
