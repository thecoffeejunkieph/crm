package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpReminderTest {

    @Test
    void body() {
        assertThat(FollowUpReminder.body(3, 1)).isEqualTo("3 due today, 1 overdue");
        assertThat(FollowUpReminder.body(2, 0)).isEqualTo("2 due today");
        assertThat(FollowUpReminder.body(0, 1)).isEqualTo("1 overdue");
    }
}
