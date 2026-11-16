package ph.thecoffeejunkie.crm.followups.rules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.followups.AutoRule;
import ph.thecoffeejunkie.crm.followups.Candidate;
import ph.thecoffeejunkie.crm.followups.FollowUpRule;
import ph.thecoffeejunkie.crm.followups.RestockRhythm;
import ph.thecoffeejunkie.crm.followups.RuleSettings;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A customer's usual reorder gap has passed since its last delivery and it has not ordered again. */
@Component
class RestockRule implements AutoRule {

    private record Delivery(long customerId, long orderId, Long repId, String number, String customer,
                            LocalDate date, boolean reordered) {
    }

    private final JdbcTemplate jdbc;

    RestockRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public FollowUpRule rule() {
        return FollowUpRule.RESTOCK;
    }

    @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
        // Newest 6 deliveries per customer is all RestockRhythm reads.
        Map<Long, List<Delivery>> byCustomer = new LinkedHashMap<>();
        jdbc.query("select customer_id, order_id, rep_id, number, display_name, dd, reordered from ("
                + "select so.customer_id, so.id order_id, so.rep_id, so.number, c.display_name, "
                + "(d.delivered_at at time zone 'Asia/Manila')::date dd, "
                + "exists (select 1 from sales_order o where o.customer_id = so.customer_id "
                + "and o.confirmed_at > d.delivered_at) reordered, "
                + "row_number() over (partition by so.customer_id order by d.delivered_at desc) rn "
                + "from delivery_order d join sales_order so on so.id = d.sales_order_id "
                + "join customer c on c.id = so.customer_id where d.status = 'DELIVERED' and c.archived_at is null) x "
                + "where rn <= 6 order by customer_id, rn", rs -> {
            byCustomer.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new Delivery(rs.getLong(1),
                    rs.getLong(2), (Long) rs.getObject(3), rs.getString(4), rs.getString(5),
                    rs.getObject(6, LocalDate.class), rs.getBoolean(7)));
        });
        List<Candidate> out = new ArrayList<>();
        for (List<Delivery> ds : byCustomer.values()) {
            Delivery last = ds.getFirst();
            int due = RestockRhythm.dueAfterDays(ds.stream().map(Delivery::date).toList(), s);
            if (last.reordered() || last.date().plusDays(due).isAfter(today)) {
                continue;
            }
            long n = ChronoUnit.DAYS.between(last.date(), today);
            out.add(new Candidate(last.customerId(), last.orderId(), last.repId(), "RESTOCK:order:" + last.orderId(),
                    "Check if " + last.customer() + " still has stock — last order " + last.number() + ", " + n
                            + " days ago", null));
        }
        return out;
    }

    @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
        return Reorders.toClose(jdbc, FollowUpRule.RESTOCK);
    }
}
