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

    record Due(long assigneeId, long dueToday, long overdue) {
    }

    /** Assignees with anything open due today or earlier. */
    List<Due> dueByAssignee(LocalDate today) {
        return jdbc.query("select assignee_id, count(*) filter (where due_date = :today) due_today, "
                + "count(*) filter (where due_date < :today) overdue from follow_up "
                + "where done_at is null and due_date <= :today group by assignee_id order by assignee_id",
                Map.of("today", today),
                (rs, n) -> new Due(rs.getLong("assignee_id"), rs.getLong("due_today"), rs.getLong("overdue")));
    }

    record Overdue(long id, long assigneeId, long customerId, String title, LocalDate dueDate, String customerName) {
    }

    /** Open follow-ups past due that nobody was told about yet. */
    List<Overdue> unnotifiedOverdue(LocalDate today) {
        return jdbc.query("select f.id, f.assignee_id, f.customer_id, f.title, f.due_date, c.display_name "
                + "from follow_up f join customer c on c.id = f.customer_id "
                + "where f.done_at is null and f.due_date < :today and f.overdue_notified_at is null order by f.id",
                Map.of("today", today), (rs, n) -> new Overdue(rs.getLong("id"), rs.getLong("assignee_id"),
                        rs.getLong("customer_id"), rs.getString("title"), rs.getObject("due_date", LocalDate.class),
                        rs.getString("display_name")));
    }

    void markOverdueNotified(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("update follow_up set overdue_notified_at = now() where id in (:ids)", Map.of("ids", ids));
        }
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
