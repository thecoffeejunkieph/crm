package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.followups.AutoRule;
import ph.thecoffeejunkie.crm.followups.Candidate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;
import ph.thecoffeejunkie.crm.followups.RuleSettings;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** The last delivery is long ago and nothing is on the way. One follow-up per last order, so a new order starts a new cycle. */
@Component
class WinBackRule implements AutoRule {

    private final JdbcTemplate jdbc;

    WinBackRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.WIN_BACK;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        return jdbc.query("select customer_id, order_id, rep_id, display_name, dd from ("
                + "select distinct on (so.customer_id) so.customer_id, so.id order_id, so.rep_id, c.display_name, "
                + "(d.delivered_at at time zone 'Asia/Manila')::date dd "
                + "from delivery_order d join sales_order so on so.id = d.sales_order_id "
                + "join customer c on c.id = so.customer_id where d.status = 'DELIVERED' and c.archived_at is null "
                + "order by so.customer_id, d.delivered_at desc) x "
                + "where dd <= ? and not exists (select 1 from sales_order o where o.customer_id = x.customer_id "
                + "and o.status in ('CONFIRMED', 'RELEASED'))", (rs, i) -> {
            long cid = rs.getLong(1);
            long order = rs.getLong(2);
            long n = ChronoUnit.DAYS.between(rs.getObject(5, LocalDate.class), today);
            return new Candidate(cid, null, (Long) rs.getObject(3), "WIN_BACK:customer:" + cid + ":" + order,
                    rs.getString(4) + " hasn't ordered in " + n + " days — check in", null);
        }, java.sql.Date.valueOf(today.minusDays(s.winBackDays())));
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Reorders.toClose(jdbc, FollowUpRule.WIN_BACK);
    }
}
