package ph.thecoffeejunkie.crm.followups;

import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Follow-ups are bucketed by the Manila calendar date, never UTC. */
final class FollowUpDay {

    static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private FollowUpDay() {
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(MANILA));
    }

    enum Bucket {
        OVERDUE, TODAY, UPCOMING, DONE;

        static Bucket parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return TODAY;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Unknown bucket: " + raw);
            }
        }
    }
}
