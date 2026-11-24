package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 07:00 Manila bell for admins when the set of products due for reorder differs from the last reminder's. The state
 * row is locked for the run, so a second instance waits and then sees the new set.
 */
@Component
class ReorderReminderSchedule {

    private final SuggestionQuery suggestions;
    private final Notifications notifications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    ReorderReminderSchedule(SuggestionQuery suggestions, Notifications notifications, JdbcTemplate jdbc,
                            PlatformTransactionManager txm) {
        this.suggestions = suggestions;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Manila")
    public void run() {
        tx.executeWithoutResult(s -> {
            var groups = suggestions.compute().groups();
            Long[] ids = groups.stream().flatMap(g -> g.items().stream()).map(i -> i.productId()).sorted()
                    .toArray(Long[]::new);
            List<Long[]> last = jdbc.query("select product_ids from reorder_reminder_state where id = 1 for update",
                    (rs, i) -> (Long[]) rs.getArray(1).getArray());
            if (!last.isEmpty() && java.util.Arrays.equals(last.get(0), ids)) {
                return;
            }
            // Always record the set, even an empty one, so items that come back later notify again.
            jdbc.update("insert into reorder_reminder_state (id, product_ids, sent_at) "
                    + "values (1, ?, now()) on conflict (id) do update set product_ids = excluded.product_ids, "
                    + "sent_at = excluded.sent_at", ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids)));
            if (ids.length == 0) {
                return;
            }
            notifications.toRole(Role.ADMIN, NotificationType.REORDER_DUE, "REORDER", null,
                    ids.length + (ids.length == 1 ? " item" : " items") + " to reorder across " + groups.size()
                            + (groups.size() == 1 ? " supplier" : " suppliers"), body(groups));
        });
    }

    private static String body(List<SuggestionResponse.SuggestionGroup> groups) {
        String names = groups.stream().limit(3).map(g -> g.supplierName() == null ? "No supplier" : g.supplierName())
                .collect(Collectors.joining(", "));
        return groups.size() > 3 ? names + " and " + (groups.size() - 3) + " more" : names;
    }
}
