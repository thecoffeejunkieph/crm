package ph.thecoffeejunkie.crm.followups;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.DistributedLock;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One bell per assignee at 08:00 Manila with what is due today or overdue; at most once a day across instances. */
@Component
class FollowUpReminder {

    private final FollowUpQueries queries;
    private final Notifications notifications;
    private final DistributedLock lock;
    private final Clock clock;

    FollowUpReminder(FollowUpQueries queries, Notifications notifications, DistributedLock lock, Clock clock) {
        this.queries = queries;
        this.notifications = notifications;
        this.lock = lock;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila")
    void run() {
        sendFor(FollowUpDay.today(clock));
    }

    /** Returns how many notifications went out; 0 when today's run already happened. */
    @Transactional
    int sendFor(LocalDate today) {
        // The lock is never released: it is the "already sent today" marker until it expires.
        if (!lock.tryLock("follow-ups-due:" + today, Duration.ofHours(23))) {
            return 0;
        }
        int sent = 0;
        for (FollowUpQueries.Due d : queries.dueByAssignee(today)) {
            notifications.toUser(d.assigneeId(), NotificationType.FOLLOW_UPS_DUE, FollowUpService.ENTITY, null,
                    "Follow-ups due", body(d.dueToday(), d.overdue()));
            sent++;
        }
        return sent;
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
