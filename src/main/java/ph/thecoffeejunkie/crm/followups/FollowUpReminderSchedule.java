package ph.thecoffeejunkie.crm.followups;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** The 08:00 Manila trigger, switched off in tests with the same property as the auto scan. */
@Component
@ConditionalOnProperty(name = "app.followups.auto.scheduled", havingValue = "true", matchIfMissing = true)
class FollowUpReminderSchedule {

    private final FollowUpReminder reminder;
    private final Clock clock;

    FollowUpReminderSchedule(FollowUpReminder reminder, Clock clock) {
        this.reminder = reminder;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Manila")
    void run() {
        reminder.sendFor(FollowUpDay.today(clock));
    }
}
