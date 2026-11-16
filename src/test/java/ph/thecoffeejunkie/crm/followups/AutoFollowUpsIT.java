package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.LongNode;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.settings.SettingKey;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine, driven by fake rules that exist only in this class's context (a nested {@code @TestConfiguration}), so
 * no other test class ever scans with them.
 */
@IntegrationTest
class AutoFollowUpsIT {

    static class FakeRule implements AutoRule {
        final FollowUpRule rule;
        List<Candidate> candidates = new ArrayList<>();
        Map<Long, String> closes = new HashMap<>();
        boolean fails; // off unless a test switches it on

        FakeRule(FollowUpRule rule) {
            this.rule = rule;
        }

        @Override public FollowUpRule rule() {
            return rule;
        }

        @Override public List<Candidate> find(LocalDate today, RuleSettings s) {
            if (fails) {
                throw new IllegalStateException("boom");
            }
            return candidates;
        }

        @Override public Map<Long, String> toClose(LocalDate today, RuleSettings s) {
            if (fails) {
                throw new IllegalStateException("boom");
            }
            return closes;
        }
    }

    @TestConfiguration
    static class Fakes {
        @Bean FakeRule fakeRestock() {
            return new FakeRule(FollowUpRule.RESTOCK);
        }

        @Bean @Order(Ordered.HIGHEST_PRECEDENCE) FakeRule failingWinBack() { // first, so later rules prove they still run
            return new FakeRule(FollowUpRule.WIN_BACK);
        }
    }

    static final AtomicInteger DAYS = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Settings settings;
    @Autowired AutoFollowUps engine;
    @Autowired FakeRule fakeRestock;
    @Autowired FakeRule failingWinBack;

    FollowUpsFixture f;
    final List<LocalDate> usedDays = new ArrayList<>();

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        fakeRestock.candidates = new ArrayList<>();
        fakeRestock.closes = new HashMap<>();
        failingWinBack.fails = false;
    }

    @AfterEach
    void cleanUp() {
        usedDays.forEach(d -> jdbc.update("delete from auto_follow_up_run where day = ?", d));
        jdbc.update("delete from app_setting where key like 'followUps.%'");
        f.cleanUp();
    }

    /** A day no other test or seed uses; each call is a fresh one so the day's marker never collides. */
    LocalDate newDay() {
        LocalDate d = LocalDate.of(2032, 1, 1).plusDays(DAYS.incrementAndGet());
        usedDays.add(d);
        return d;
    }

    String key(String suffix) {
        return f.tag + ":" + suffix;
    }

    Candidate cand(long customerId, Long repHint, String suffix) {
        return new Candidate(customerId, null, repHint, key(suffix), "Check " + suffix, null);
    }

    List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from follow_up where auto_key like ? order by auto_key", f.tag + ":%");
    }

    Map<String, Object> row(String suffix) {
        return jdbc.queryForMap("select * from follow_up where auto_key = ?", key(suffix));
    }

    @Test
    void createsOneRowPerCandidateDueToday() {
        long rep = f.user("rep", Role.SALES);
        fakeRestock.candidates = List.of(cand(f.customer("a", rep), null, "1"), cand(f.customer("b", rep), null, "2"));
        LocalDate day = newDay();

        engine.run(day);

        assertThat(rows()).hasSize(2).allSatisfy(r -> {
            assertThat(r.get("due_date")).hasToString(day.toString());
            assertThat(r.get("created_by")).isNull();
            assertThat(r.get("rule")).isEqualTo("RESTOCK");
            assertThat(r.get("assignee_id")).isEqualTo(rep);
            assertThat(r.get("done_at")).isNull();
        });
        long id = (Long) row("1").get("id");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'FOLLOW_UP_CREATED' "
                + "and actor_type = 'SYSTEM' and summary = 'auto: RESTOCK' and entity_id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void sameKeyIsNeverCreatedTwice() {
        long rep = f.user("rep", Role.SALES);
        fakeRestock.candidates = List.of(cand(f.customer("a", rep), null, "1"));

        engine.run(newDay());
        jdbc.update("update follow_up set done_at = now(), done_by = ? where auto_key = ?", rep, key("1")); // rep ticks it
        engine.run(newDay());

        assertThat(rows()).hasSize(1);
    }

    @Test
    void secondRunSameDayDoesNothing() {
        long rep = f.user("rep", Role.SALES);
        long c = f.customer("a", rep);
        fakeRestock.candidates = List.of(cand(c, null, "1"));
        LocalDate day = newDay();

        engine.run(day);
        fakeRestock.candidates = List.of(cand(c, null, "1"), cand(c, null, "2"));
        engine.run(day);

        assertThat(rows()).hasSize(1);
    }

    @Test
    void assigneeFallsBackRepThenHintThenAdmin() {
        long rep = f.user("rep", Role.SALES);
        long gone = f.user("gone", Role.SALES);
        jdbc.update("update app_user set archived_at = now() where id = ?", gone);
        long hint = f.user("hint", Role.SALES);
        long admin = f.user("admin", Role.SALES);
        jdbc.update("insert into user_role (user_id, role) values (?, 'ADMIN')", admin); // several roles are fine
        settings.update(Map.of(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID, LongNode.valueOf(admin)));
        fakeRestock.candidates = List.of(
                cand(f.customer("withRep", rep), hint, "rep"),
                cand(f.customer("repGone", gone), hint, "hint"),
                cand(f.customer("noRep", null), null, "admin"),
                cand(f.customer("noRepHintGone", null), gone, "admin2"));

        engine.run(newDay());

        assertThat(row("rep").get("assignee_id")).isEqualTo(rep);
        assertThat(row("hint").get("assignee_id")).isEqualTo(hint);
        assertThat(row("admin").get("assignee_id")).isEqualTo(admin);
        assertThat(row("admin2").get("assignee_id")).isEqualTo(admin);
    }

    @Test
    void noAssigneeSkips() {
        fakeRestock.candidates = List.of(cand(f.customer("orphan", null), null, "1"));

        engine.run(newDay());
        assertThat(rows()).isEmpty();
    }

    @Test
    void inactiveFallbackAdminSkips() {
        long archivedAdmin = f.user("old", Role.ADMIN);
        jdbc.update("update app_user set archived_at = now() where id = ?", archivedAdmin);
        long salesOnly = f.user("sales", Role.SALES);
        long c = f.customer("orphan", null);
        fakeRestock.candidates = List.of(cand(c, null, "1"));

        settings.update(Map.of(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID, LongNode.valueOf(archivedAdmin)));
        engine.run(newDay());

        settings.update(Map.of(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID, LongNode.valueOf(salesOnly))); // not an ADMIN
        engine.run(newDay());
        assertThat(rows()).isEmpty();
    }

    @Test
    void archivedCustomerSkipped() {
        long rep = f.user("rep", Role.SALES);
        long c = f.customer("gone", rep);
        jdbc.update("update customer set archived_at = now() where id = ?", c);
        fakeRestock.candidates = List.of(cand(c, null, "1"));

        engine.run(newDay());
        assertThat(rows()).isEmpty();
    }

    @Test
    void disabledRuleCreatesNothing() {
        long rep = f.user("rep", Role.SALES);
        fakeRestock.candidates = List.of(cand(f.customer("a", rep), null, "1"));
        settings.update(Map.of(SettingKey.FOLLOW_UPS_AUTO_RESTOCK_ENABLED, BooleanNode.FALSE));

        engine.run(newDay());
        assertThat(rows()).isEmpty();
    }

    @Test
    void failingRuleDoesNotStopOthers() {
        long rep = f.user("rep", Role.SALES);
        fakeRestock.candidates = List.of(cand(f.customer("a", rep), null, "1"));
        failingWinBack.fails = true;

        engine.run(newDay());
        assertThat(rows()).hasSize(1);
    }

    @Test
    void closeSetsReasonAndLeavesDoneRowsAlone() {
        long rep = f.user("rep", Role.SALES);
        long c = f.customer("a", rep);
        fakeRestock.candidates = List.of(cand(c, null, "open"), cand(c, null, "done"), cand(c, null, "other"));
        engine.run(newDay());
        jdbc.update("update follow_up set done_at = now() - interval '1 day', done_by = ? where auto_key = ?", rep, key("done"));
        fakeRestock.candidates = List.of();
        fakeRestock.closes = Map.of((Long) row("open").get("id"), "Customer reordered",
                (Long) row("done").get("id"), "Customer reordered");

        engine.run(newDay());

        assertThat(row("open").get("done_at")).isNotNull();
        assertThat(row("open").get("done_by")).isNull();
        assertThat(row("open").get("closed_reason")).isEqualTo("Customer reordered");
        assertThat(row("done").get("done_by")).isEqualTo(rep);
        assertThat(row("done").get("closed_reason")).isNull();
        assertThat(row("other").get("done_at")).isNull();
    }

    @Test
    void closeByKeyOrPrefixClosesOnlyOpenAutoRows() {
        long rep = f.user("rep", Role.SALES);
        long c = f.customer("a", rep);
        fakeRestock.candidates = List.of(cand(c, null, "x:1"), cand(c, null, "x:2"), cand(c, null, "y:1"));
        engine.run(newDay());
        long manual = f.followUp(c, null, rep, "manual", LocalDate.now(), null);

        assertThat(engine.close(key("y:1"), false, "Gone")).isEqualTo(1);
        assertThat(engine.close(f.tag + ":x:", true, "Gone")).isEqualTo(2);
        assertThat(engine.close(f.tag + ":x:", true, "Gone")).isZero();

        assertThat(rows()).allSatisfy(r -> assertThat(r.get("closed_reason")).isEqualTo("Gone"));
        assertThat(jdbc.queryForObject("select done_at from follow_up where id = ?", Object.class, manual)).isNull();
    }
}
