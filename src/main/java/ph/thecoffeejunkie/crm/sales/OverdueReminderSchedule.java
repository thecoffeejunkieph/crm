package ph.thecoffeejunkie.crm.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** The 09:00 Manila trigger; off in tests, which call {@link OverdueReminders#sendFor} with a fixed date. */
@Component
@ConditionalOnProperty(name = "app.sales.overdue-reminders.scheduled", havingValue = "true", matchIfMissing = true)
class OverdueReminderSchedule {

    private final OverdueReminders reminders;
    private final Clock clock;

    OverdueReminderSchedule(OverdueReminders reminders, Clock clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Manila")
    void run() {
        reminders.sendFor(SalesTime.today(clock));
    }
}