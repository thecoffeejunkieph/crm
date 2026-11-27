package ph.thecoffeejunkie.crm.followups;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** The 07:45 Manila trigger, split out so a test context can switch it off (app.followups.auto.scheduled=false). */
@Component
@ConditionalOnProperty(name = "app.followups.auto.scheduled", havingValue = "true", matchIfMissing = true)
class AutoFollowUpsSchedule {

    private final AutoFollowUps engine;
    private final Clock clock;

    AutoFollowUpsSchedule(AutoFollowUps engine, Clock clock) {
        this.engine = engine;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 7 * * *", zone = "Asia/Manila")
    void run() {
        engine.run(FollowUpDay.today(clock));
    }
}
