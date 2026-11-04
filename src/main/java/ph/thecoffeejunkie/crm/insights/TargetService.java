package ph.thecoffeejunkie.crm.insights;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Monthly targets per SALES user (spec 2026-11-05). Every write clears the dashboard cache once it commits. */
@Service
class TargetService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final SalesTargetRepository repo;
    private final Users users;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final CacheManager caches;

    TargetService(SalesTargetRepository repo, Users users, AuditLog audit, CurrentActor actor, CacheManager caches) {
        this.caches = caches;
        this.repo = repo;
        this.users = users;
        this.audit = audit;
        this.actor = actor;
    }

    @Transactional(readOnly = true)
    List<TargetRow> list(YearMonth m) {
        List<Long> reps = users.activeIdsWithRole(Role.SALES);
        Map<Long, String> names = users.displayNames(reps);
        Map<Long, SalesTarget> rows = repo.findByMonth(m.atDay(1)).stream()
                .collect(Collectors.toMap(t -> t.repId, Function.identity()));
        return reps.stream().map(id -> row(id, names.get(id), rows.get(id)))
                .sorted((a, b) -> Objects.toString(a.repName(), "").compareToIgnoreCase(Objects.toString(b.repName(), "")))
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<SalesTarget> find(long repId, YearMonth m) {
        return repo.findByRepIdAndMonth(repId, m.atDay(1));
    }

    @Transactional
    TargetRow set(long repId, YearMonth m, TargetRequest req) {
        if (!users.isActiveWithRole(repId, Role.SALES)) {
            throw new DomainException(ErrorCode.INSIGHTS_NOT_A_REP, "Targets can only be set for active sales users.");
        }
        Optional<SalesTarget> existing = repo.findByRepIdAndMonth(repId, m.atDay(1));
        if (existing.isPresent() != (req.version() != null)
                || existing.isPresent() && existing.get().version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This target was changed by someone else. Reload and retry.");
        }
        SalesTarget t = existing.orElseGet(() -> {
            SalesTarget n = new SalesTarget();
            n.repId = repId;
            n.month = m.atDay(1);
            return n;
        });
        t.targetAmount = req.targetAmount();
        t.commissionPct = req.commissionPct();
        t.updatedAt = Instant.now();
        t.updatedBy = actor.scope().userId();
        repo.saveAndFlush(t);
        evictDashboardsAfterCommit();
        String name = users.displayNames(List.of(repId)).get(repId);
        audit.record(AuditAction.SALES_TARGET_SET, "SALES_TARGET", t.id, "Set " + name + "'s target for "
                + m.format(MONTH) + " to " + t.targetAmount.toPlainString() + " at " + t.commissionPct.toPlainString()
                + "%", null);
        return row(repId, name, t);
    }

    /** Copies into {@code to} only for reps with no row there; returns how many were copied. */
    @Transactional
    int copy(YearMonth from, YearMonth to) {
        Map<Long, SalesTarget> there = repo.findByMonth(to.atDay(1)).stream()
                .collect(Collectors.toMap(t -> t.repId, Function.identity()));
        int copied = 0;
        for (SalesTarget src : repo.findByMonth(from.atDay(1))) {
            if (there.containsKey(src.repId) || !users.isActiveWithRole(src.repId, Role.SALES)) {
                continue;
            }
            SalesTarget t = new SalesTarget();
            t.repId = src.repId;
            t.month = to.atDay(1);
            t.targetAmount = src.targetAmount;
            t.commissionPct = src.commissionPct;
            t.updatedBy = actor.scope().userId();
            repo.save(t);
            copied++;
        }
        evictDashboardsAfterCommit();
        audit.record(AuditAction.SALES_TARGET_SET, "SALES_TARGET", null, "Copied " + copied + " targets from "
                + from.format(MONTH) + " to " + to.format(MONTH), null);
        return copied;
    }

    /**
     * Cached dashboards show the old target until this runs. Not {@code @CacheEvict(allEntries = true)}: with
     * RedisCache that calls {@link Cache#clear()}, which may defer and in practice left entries in place;
     * {@link Cache#invalidate()} removes them now.
     */
    private void evictDashboardsAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Cache c = caches.getCache("insights");
                if (c != null) {
                    try {
                        c.invalidate();
                    } catch (RuntimeException e) {
                        // A cache outage only delays the new target by the TTL; the write already committed.
                        org.slf4j.LoggerFactory.getLogger(TargetService.class).warn("Dashboard cache not cleared", e);
                    }
                }
            }
        });
    }

    private static TargetRow row(long repId, String name, SalesTarget t) {
        return t == null ? new TargetRow(repId, name, null, null, null)
                : new TargetRow(repId, name, t.targetAmount, t.commissionPct, t.version);
    }
}
