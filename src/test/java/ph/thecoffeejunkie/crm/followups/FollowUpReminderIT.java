package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class FollowUpReminderIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired FollowUpReminder reminder;

    FollowUpsFixture f;
    // A day nobody else's data lands on, so other tests' follow-ups don't count here.
    final LocalDate day = LocalDate.of(2031, 3, 14);

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        redis.delete("lock:follow-ups-due:" + day);
        jdbc.update("delete from follow_up_reminder_run where day = ?", day);
    }

    @AfterEach
    void cleanUp() {
        redis.delete("lock:follow-ups-due:" + day);
        jdbc.update("delete from follow_up_reminder_run where day = ?", day);
        f.cleanUp();
    }

    private List<String> bodies(long userId) {
        return jdbc.queryForList("select body from notification where recipient_user_id = ? and type = 'FOLLOW_UPS_DUE'",
                String.class, userId);
    }

    @Test
    void onePerAssignee() {
        long a = f.user("a", Role.SALES);
        long b = f.user("b", Role.SALES);
        long acme = f.customer("acme", a);
        f.followUp(acme, null, a, "t1", day, null);
        f.followUp(acme, null, a, "t2", day, null);
        f.followUp(acme, null, a, "late", day.minusDays(2), null);
        f.followUp(acme, null, a, "done", day, Instant.now());
        f.followUp(acme, null, b, "later", day.plusDays(1), null);
        reminder.sendFor(day);
        assertThat(bodies(a)).containsExactly("2 due today, 1 overdue");
        assertThat(bodies(b)).isEmpty();
    }

    @Test
    void secondRunSameDaySendsNothing() {
        long a = f.user("a", Role.SALES);
        f.followUp(f.customer("acme", a), null, a, "t1", day, null);
        assertThat(reminder.sendFor(day)).isPositive();
        assertThat(reminder.sendFor(day)).isZero();
        assertThat(bodies(a)).hasSize(1);
    }

    @Test
    void sentOnceEvenWhenRedisLosesTheMarker() {
        long a = f.user("a", Role.SALES);
        f.followUp(f.customer("acme", a), null, a, "t1", day, null);
        assertThat(reminder.sendFor(day)).isPositive();
        redis.delete("lock:follow-ups-due:" + day); // Redis down/flushed, or a second instance whose lock failed open
        assertThat(reminder.sendFor(day)).isZero();
        assertThat(bodies(a)).hasSize(1);
    }

    private List<java.util.Map<String, Object>> overdue(long userId) {
        return jdbc.queryForList("select title, body, entity_type, entity_id from notification where recipient_user_id = ? "
                + "and type = 'FOLLOW_UP_OVERDUE'", userId);
    }

    @Test
    void overdueSentOnceTheDayAfterDue() {
        long a = f.user("a", Role.SALES);
        long acme = f.customer("acme", a);
        f.followUp(acme, null, a, "Call back", day.minusDays(1), null);
        reminder.sendFor(day);
        assertThat(overdue(a)).hasSize(1);
        var n = overdue(a).get(0);
        assertThat(n.get("title")).isEqualTo("Overdue: Call back");
        assertThat((String) n.get("body")).startsWith("Due 13 Mar · ").endsWith("acme");
        assertThat(n.get("entity_type")).isEqualTo("CUSTOMER");
        assertThat(((Number) n.get("entity_id")).longValue()).isEqualTo(acme);
    }

    @Test
    void notSentAgainNextDay() {
        long a = f.user("a", Role.SALES);
        f.followUp(f.customer("acme", a), null, a, "Call back", day.minusDays(1), null);
        reminder.sendFor(day);
        jdbc.update("delete from follow_up_reminder_run where day = ?", day.plusDays(1));
        try {
            reminder.sendFor(day.plusDays(1));
        } finally {
            jdbc.update("delete from follow_up_reminder_run where day = ?", day.plusDays(1));
        }
        assertThat(overdue(a)).hasSize(1);
    }

    @Test
    void manualAndAutoBothNotified() {
        long a = f.user("a", Role.SALES);
        long acme = f.customer("acme", a);
        long manual = f.followUp(acme, null, a, "Manual", day.minusDays(1), null);
        long auto = f.followUp(acme, null, a, "Auto", day.minusDays(2), null);
        jdbc.update("update follow_up set rule = 'WIN_BACK' where id = ?", auto);
        reminder.sendFor(day);
        assertThat(overdue(a)).extracting(m -> m.get("title")).containsExactlyInAnyOrder("Overdue: Manual", "Overdue: Auto");
        assertThat(manual).isNotEqualTo(auto);
    }

    @Test
    void oldOverdueMarkedWithoutSending() {
        long a = f.user("a", Role.SALES);
        long old = f.followUp(f.customer("acme", a), null, a, "Ancient", day.minusDays(10), null);
        long done = f.followUp(f.customer("acme2", a), null, a, "Done", day.minusDays(2), Instant.now());
        reminder.sendFor(day);
        assertThat(overdue(a)).isEmpty();
        assertThat(jdbc.queryForObject("select overdue_notified_at is not null from follow_up where id = ?", Boolean.class, old)).isTrue();
        assertThat(jdbc.queryForObject("select overdue_notified_at is null from follow_up where id = ?", Boolean.class, done)).isTrue();
    }

    @Test
    void summaryStillSent() {
        long a = f.user("a", Role.SALES);
        f.followUp(f.customer("acme", a), null, a, "late", day.minusDays(1), null);
        reminder.sendFor(day);
        assertThat(bodies(a)).containsExactly("1 overdue");
    }
}
