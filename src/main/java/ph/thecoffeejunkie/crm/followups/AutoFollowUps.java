package ph.thecoffeejunkie.crm.followups;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.identity.UserSummary;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.settings.Settings;
import ph.thecoffeejunkie.crm.shared.Role;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The daily scan that creates and closes automatic follow-ups (spec 2026-11-17). Like {@link FollowUpReminder}, a
 * marker row makes one instance win each day; unlike it, the marker commits first, because each rule has its own
 * transaction and one rule failing must not undo or block the others.
 */
@Component
class AutoFollowUps {

    private static final Logger log = LoggerFactory.getLogger(AutoFollowUps.class);

    private final List<AutoRule> rules;
    private final Settings settings;
    private final Users users;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    AutoFollowUps(List<AutoRule> rules, Settings settings, Users users, AuditLog audit, JdbcTemplate jdbc,
                  PlatformTransactionManager txm, Clock clock) {
        this.rules = rules;
        this.settings = settings;
        this.users = users;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 7 * * *", zone = "Asia/Manila")
    void run() {
        run(FollowUpDay.today(clock));
    }

    /** Returns how many follow-ups were created; 0 when today's run already happened. */
    int run(LocalDate today) {
        Boolean first = tx.execute(s ->
                jdbc.update("insert into auto_follow_up_run (day) values (?) on conflict do nothing", today) == 1);
        if (!Boolean.TRUE.equals(first)) {
            return 0;
        }
        RuleSettings cfg = RuleSettings.from(settings);
        int created = 0;
        for (AutoRule r : rules) {
            if (!cfg.enabled().contains(r.rule())) {
                continue;
            }
            try {
                Integer n = tx.execute(s -> create(r, today, cfg));
                created += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.error("Auto follow-up rule {} failed while creating", r.rule(), e);
            }
        }
        for (AutoRule r : rules) {
            if (!cfg.enabled().contains(r.rule())) {
                continue;
            }
            try {
                tx.executeWithoutResult(s -> r.toClose(today, cfg).forEach(this::closeId));
            } catch (RuntimeException e) {
                log.error("Auto follow-up rule {} failed while closing", r.rule(), e);
            }
        }
        return created;
    }

    /** Closes open auto follow-ups whose key equals (or starts with) the given one; returns how many. */
    public int close(String autoKeyPrefixOrExact, boolean prefix, String reason) {
        String where = prefix ? "starts_with(auto_key, ?)" : "auto_key = ?";
        Integer n = tx.execute(s -> jdbc.update("update follow_up set done_at = now(), done_by = null, closed_reason = ?, "
                + "updated_at = now(), version = version + 1 where done_at is null and rule is not null and " + where,
                reason, autoKeyPrefixOrExact));
        return n == null ? 0 : n;
    }

    private void closeId(long id, String reason) {
        jdbc.update("update follow_up set done_at = now(), done_by = null, closed_reason = ?, updated_at = now(), "
                + "version = version + 1 where id = ? and done_at is null and rule is not null", reason, id);
    }

    private int create(AutoRule r, LocalDate today, RuleSettings cfg) {
        int n = 0;
        for (Candidate c : r.find(today, cfg)) {
            Long assignee = assignee(c, cfg);
            if (assignee == null) {
                continue;
            }
            Timestamp now = Timestamp.from(Instant.now(clock));
            List<Long> ids = jdbc.queryForList("insert into follow_up (customer_id, order_id, assignee_id, title, note, "
                    + "due_date, created_by, rule, auto_key, created_at, updated_at) "
                    + "values (?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?) on conflict (auto_key) do nothing returning id",
                    Long.class, c.customerId(), c.orderId(), assignee, c.title(), c.note(), today, r.rule().name(),
                    c.key(), now, now);
            if (!ids.isEmpty()) {
                audit.record(AuditAction.FOLLOW_UP_CREATED, FollowUpService.ENTITY, ids.getFirst(),
                        "auto: " + r.rule(), null);
                n++;
            }
        }
        return n;
    }

    /** The customer's rep, else the order's rep, else the fallback admin; null (skip) for an archived customer or none. */
    private Long assignee(Candidate c, RuleSettings cfg) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select assigned_rep_id, archived_at from customer where id = ?", c.customerId());
        if (rows.isEmpty() || rows.getFirst().get("archived_at") != null) {
            return null;
        }
        Long rep = (Long) rows.getFirst().get("assigned_rep_id");
        if (rep != null && active(rep, null)) {
            return rep;
        }
        if (c.repHint() != null && active(c.repHint(), null)) {
            return c.repHint();
        }
        if (cfg.fallbackAdminId() != null && active(cfg.fallbackAdminId(), Role.ADMIN)) {
            return cfg.fallbackAdminId();
        }
        log.info("Auto follow-up {} skipped: no active assignee for customer {}", c.key(), c.customerId());
        return null;
    }

    private boolean active(long userId, Role mustHold) {
        return users.find(userId).filter(UserSummary::active)
                .filter(u -> mustHold == null || u.roles().contains(mustHold)).isPresent();
    }
}
