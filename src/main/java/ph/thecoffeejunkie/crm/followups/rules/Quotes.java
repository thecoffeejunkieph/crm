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

    /** Open quote rows whose order is no longer QUOTED at the revision in the key (auto_key = RULE:order:id:revision). */
    static Map<Long, String> toClose(JdbcTemplate jdbc, FollowUpRule rule) {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select f.id, o.number from follow_up f join sales_order o on o.id = f.order_id "
                + "where f.rule = ? and f.done_at is null "
                + "and (o.status <> 'QUOTED' or o.revision <> split_part(f.auto_key, ':', 4)::int)",
                rs -> {
                    out.put(rs.getLong(1), "Quote " + rs.getString(2) + " closed");
                }, rule.name());
        return out;
    }
}
