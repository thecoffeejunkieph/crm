package ph.thecoffeejunkie.crm.followups;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One bell per assignee at 08:00 Manila with what is due today or overdue. The day's marker row is inserted in the
 * same transaction as the notifications: one instance wins, and a failure part-way sends nothing and can be retried.
 */
@Component
class FollowUpReminder {

    private final FollowUpQueries queries;
    private final Notifications notifications;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    FollowUpReminder(FollowUpQueries queries, Notifications notifications, JdbcTemplate jdbc, PlatformTransactionManager txm,
                     Clock clock) {
        this.queries = queries;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila")
    void run() {
        sendFor(FollowUpDay.today(clock));
    }

    /** Returns how many notifications went out; 0 when today's run already happened. */
    int sendFor(LocalDate today) {
        Integer sent = tx.execute(s -> {
            if (jdbc.update("insert into follow_up_reminder_run (day) values (?) on conflict do nothing", today) == 0) {
                return 0;
            }
            int n = 0;
            for (FollowUpQueries.Due d : queries.dueByAssignee(today)) {
                notifications.toUser(d.assigneeId(), NotificationType.FOLLOW_UPS_DUE, FollowUpService.ENTITY, null,
                        "Follow-ups due", body(d.dueToday(), d.overdue()));
                n++;
            }
            return n;
        });
        return sent == null ? 0 : sent;
    }

    static String body(long dueToday, long overdue) {
        List<String> parts = new ArrayList<>();
        if (dueToday > 0) {
            parts.add(dueToday + " due today");
        }
        if (overdue > 0) {
            parts.add(overdue + " overdue");
        }
        return String.join(", ", parts);
    }
}
