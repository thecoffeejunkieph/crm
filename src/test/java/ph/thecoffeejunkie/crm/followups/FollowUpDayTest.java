package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpDayTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void today_isManilaDate() {
        assertThat(FollowUpDay.today(at("2026-11-05T15:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(FollowUpDay.today(at("2026-11-05T16:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void parse() {
        assertThat(FollowUpDay.Bucket.parse(null)).isEqualTo(FollowUpDay.Bucket.TODAY);
        assertThat(FollowUpDay.Bucket.parse("Overdue")).isEqualTo(FollowUpDay.Bucket.OVERDUE);
        assertThatThrownBy(() -> FollowUpDay.Bucket.parse("soon"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
