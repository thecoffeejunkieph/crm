package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** What the order rules share: the safety-net close for quote rows, and the copy's money and date formats. */
final class Quotes {

    static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private Quotes() {
    }

    static String money(BigDecimal amount) {
        return String.format(Locale.US, "₱%,.2f", amount);
    }

    /** The newest QUOTE_SENT audit of the order; its id keys the chase and expiry rows (RULE:order:id:sent:auditId). */
    static final String LATEST_SENT = "select a.id, (a.at at time zone 'Asia/Manila')::date d from audit_event a "
            + "where a.entity_type = 'ORDER' and a.entity_id = o.id and a.action = 'QUOTE_SENT' "
            + "order by a.at desc, a.id desc limit 1";

    /** Open quote rows whose order is no longer QUOTED, or whose key is not the order's latest send. */
    static Map<Long, String> toClose(JdbcTemplate jdbc, FollowUpRule rule) {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select f.id, o.number, o.status = 'QUOTED' from follow_up f join sales_order o on o.id = f.order_id "
                + "where f.rule = ? and f.done_at is null and (o.status <> 'QUOTED' or split_part(f.auto_key, ':', 5)::bigint "
                + "is distinct from (select s.id from (" + LATEST_SENT + ") s))",
                rs -> {
                    out.put(rs.getLong(1), "Quote " + rs.getString(2) + (rs.getBoolean(3) ? " resent" : " closed"));
                }, rule.name());
        return out;
    }
}
