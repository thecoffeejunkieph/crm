package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;

import java.util.HashMap;
import java.util.Map;

/** The safety-net close shared by RESTOCK and WIN_BACK: the customer confirmed an order after the row was created. */
final class Reorders {

    private Reorders() {
    }

    static Map<Long, String> toClose(JdbcTemplate jdbc, FollowUpRule rule) {
        Map<Long, String> out = new HashMap<>();
        jdbc.query("select f.id, c.display_name, o.number from follow_up f join customer c on c.id = f.customer_id "
                + "join lateral (select number from sales_order o where o.customer_id = f.customer_id "
                + "and o.confirmed_at > f.created_at and o.status <> 'CANCELLED' order by o.confirmed_at desc limit 1) o on true "
                + "where f.rule = ? and f.done_at is null",
                rs -> {
                    out.put(rs.getLong(1), rs.getString(2) + " ordered " + rs.getString(3));
                }, rule.name());
        return out;
    }
}
