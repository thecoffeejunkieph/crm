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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final SalesCategoryTargetRepository categoryRepo;
    private final InsightsQueries q;

    TargetService(SalesTargetRepository repo, Users users, AuditLog audit, CurrentActor actor, CacheManager caches,
                  SalesCategoryTargetRepository categoryRepo, InsightsQueries q) {
        this.categoryRepo = categoryRepo;
        this.q = q;
        this.caches = caches;
        this.repo = repo;
        this.users = users;
        this.audit = audit;
        this.actor = actor;
    }

    @Transactional(readOnly = true)
    List<TargetRow> list(YearMonth m) {
        return list(m, List.of());
    }

    /**
     * Active SALES users, plus anyone with a target that month and {@code alsoInclude}, so a rep who has since left
     * still shows in past months.
     */
    @Transactional(readOnly = true)
    List<TargetRow> list(YearMonth m, Collection<Long> alsoInclude) {
        Map<Long, SalesTarget> rows = repo.findByMonth(m.atDay(1)).stream()
                .collect(Collectors.toMap(t -> t.repId, Function.identity()));
        Set<Long> ids = new LinkedHashSet<>(users.activeIdsWithRole(Role.SALES));
        ids.addAll(rows.keySet());
        ids.addAll(alsoInclude);
        List<Long> reps = List.copyOf(ids);
        Map<Long, String> names = users.displayNames(reps);
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

    /** Every active category plus any with a target that month. No rep check, so past reps still list. */
    @Transactional(readOnly = true)
    List<CategoryTargetRow> categoryTargets(long repId, YearMonth m) {
        Map<Long, SalesCategoryTarget> rows = categoryRepo.findByRepIdAndMonth(repId, m.atDay(1)).stream()
                .collect(Collectors.toMap(t -> t.categoryId, Function.identity()));
        return q.categories(rows.keySet()).stream().map(c -> categoryRow(c, rows.get(c.id()))).toList();
    }

    @Transactional
    CategoryTargetRow setCategoryTarget(long repId, YearMonth m, long categoryId, CategoryTargetRequest req) {
        if (!users.isActiveWithRole(repId, Role.SALES)) {
            throw new DomainException(ErrorCode.INSIGHTS_NOT_A_REP, "Targets can only be set for active sales users.");
        }
        InsightsQueries.Category cat = q.category(categoryId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "That category doesn't exist."));
        if (cat.archived()) {
            // INVALID_STATE, not VALIDATION_FAILED: the spec wants 422 and VALIDATION_FAILED maps to 400.
            throw new DomainException(ErrorCode.INVALID_STATE, "That category is archived.");
        }
        Optional<SalesCategoryTarget> existing = categoryRepo.findByRepIdAndMonthAndCategoryId(repId, m.atDay(1), categoryId);
        if (existing.isPresent() != (req.version() != null)
                || existing.isPresent() && existing.get().version != req.version()) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This target was changed by someone else. Reload and retry.");
        }
        SalesCategoryTarget t = existing.orElseGet(() -> {
            SalesCategoryTarget n = new SalesCategoryTarget();
            n.repId = repId;
            n.month = m.atDay(1);
            n.categoryId = categoryId;
            return n;
        });
        t.targetAmount = req.targetAmount();
        t.updatedAt = Instant.now();
        t.updatedBy = actor.scope().userId();
        categoryRepo.saveAndFlush(t);
        evictDashboardsAfterCommit();
        String name = users.displayNames(List.of(repId)).get(repId);
        audit.record(AuditAction.SALES_TARGET_SET, "SALES_CATEGORY_TARGET", t.id, "Set " + name + "'s " + cat.name()
                + " target for " + m.format(MONTH) + " to " + t.targetAmount.toPlainString(), null);
        return categoryRow(cat, t);
    }

    @Transactional
    void clearCategoryTarget(long repId, YearMonth m, long categoryId, long version) {
        SalesCategoryTarget t = categoryRepo.findByRepIdAndMonthAndCategoryId(repId, m.atDay(1), categoryId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "That target doesn't exist."));
        if (t.version != version) {
            throw new DomainException(ErrorCode.STALE_VERSION, "This target was changed by someone else. Reload and retry.");
        }
        categoryRepo.delete(t);
        categoryRepo.flush();
        evictDashboardsAfterCommit();
        String name = users.displayNames(List.of(repId)).get(repId);
        String category = q.category(categoryId).map(InsightsQueries.Category::name).orElse("category " + categoryId);
        audit.record(AuditAction.SALES_TARGET_SET, "SALES_CATEGORY_TARGET", t.id, "Cleared " + name + "'s " + category
                + " target for " + m.format(MONTH), null);
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

    private static CategoryTargetRow categoryRow(InsightsQueries.Category c, SalesCategoryTarget t) {
        return new CategoryTargetRow(c.id(), c.name(), c.archived(), t == null ? null : t.targetAmount,
                t == null ? null : t.version);
    }

    private static TargetRow row(long repId, String name, SalesTarget t) {
        return t == null ? new TargetRow(repId, name, null, null, null)
                : new TargetRow(repId, name, t.targetAmount, t.commissionPct, t.version);
    }
}
