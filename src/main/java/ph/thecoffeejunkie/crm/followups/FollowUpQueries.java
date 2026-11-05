package ph.thecoffeejunkie.crm.followups;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The SQL follow-ups needs beyond its own entity: order lookups (sales has no public facade for them) and counts. */
@Component
class FollowUpQueries {

    private final NamedParameterJdbcTemplate jdbc;

    FollowUpQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Long> orderCustomer(long orderId) {
        List<Long> r = jdbc.queryForList("select customer_id from sales_order where id = :id",
                Map.of("id", orderId), Long.class);
        return r.stream().findFirst();
    }

    Map<Long, String> orderNumbers(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("select id, number from sales_order where id in (:ids)", Map.of("ids", ids),
                rs -> { out.put(rs.getLong("id"), rs.getString("number")); });
        return out;
    }

    /** {@code assigneeId} null = everyone (ADMIN); customer and order filters are optional. */
    FollowUpCounts counts(Long assigneeId, Long customerId, Long orderId, LocalDate today) {
        MapSqlParameterSource p = new MapSqlParameterSource("today", today).addValue("a", assigneeId)
                .addValue("c", customerId).addValue("o", orderId);
        return jdbc.queryForObject("select count(*) filter (where due_date < :today) overdue, "
                + "count(*) filter (where due_date = :today) today, count(*) filter (where due_date > :today) upcoming "
                + "from follow_up where done_at is null and (cast(:a as bigint) is null or assignee_id = :a) "
                + "and (cast(:c as bigint) is null or customer_id = :c) and (cast(:o as bigint) is null or order_id = :o)",
                p, (rs, n) -> new FollowUpCounts(rs.getLong("overdue"), rs.getLong("today"), rs.getLong("upcoming")));
    }
}
