package ph.thecoffeejunkie.crm.insights;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** One read per role, cached 60s in Redis (cache "insights"); target writes invalidate it (TargetService). */
@Service
class InsightsService {

    private final InsightsQueries q;
    private final TargetService targets;
    private final Clock clock;

    InsightsService(InsightsQueries q, TargetService targets, Clock clock) {
        this.q = q;
        this.targets = targets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "insights", key = "'sales:' + #userId + ':' + #m")
    public SalesInsights sales(long userId, YearMonth m) {
        BigDecimal collected = q.collected(userId, m);
        SalesInsights.Target target = targets.find(userId, m)
                .map(t -> new SalesInsights.Target(t.targetAmount, t.commissionPct)).orElse(null);
        BigDecimal amount = target == null ? null : target.amount();
        BigDecimal pct = target == null ? null : target.commissionPct();
        InsightsQueries.Balance owed = q.outstanding(userId, InsightsMonth.today(clock));
        return new SalesInsights(m, target, collected, Commission.progressPct(collected, amount),
                Commission.remaining(collected, amount), Commission.hit(collected, amount),
                Commission.of(collected, amount, pct), q.invoiced(userId, m), owed.outstanding(), owed.overdue(),
                q.toVerify(userId), q.collectedByDay(userId, m),
                q.needsAction(userId, InsightsMonth.today(clock), 5), q.topCustomers(userId, m, 5));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "insights", key = "'admin:' + #m")
    public AdminInsights admin(YearMonth m) {
        BigDecimal collected = q.collected(null, m);
        InsightsQueries.Balance owed = q.outstanding(null, InsightsMonth.today(clock));
        return new AdminInsights(m, collected, q.collected(null, m.minusMonths(1)), q.invoiced(null, m), q.margin(m),
                owed.outstanding(), owed.overdue(), q.toVerify(null), q.collectedByMonth(m, 12), reps(m),
                q.topProducts(m, 10), q.topCustomers(null, m, 10), q.operations());
    }

    /** {@code warehouseIds} null for ADMIN (every warehouse). The key sorts the ids so set order never splits it. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "insights",
            key = "'wh:' + (#warehouseIds == null ? 'all' : new java.util.TreeSet(#warehouseIds)) + ':' + #today")
    public WarehouseInsights warehouse(Set<Long> warehouseIds, LocalDate today) {
        return q.warehouse(warehouseIds, today);
    }

    /** Every active SALES user (zeros included), anyone credited or targeted that month, then "Unassigned" when it collected anything; by collected desc. */
    private List<AdminInsights.RepRow> reps(YearMonth m) {
        Map<Long, BigDecimal> byRep = q.collectedByRep(m);
        List<Long> credited = byRep.keySet().stream().filter(Objects::nonNull).toList();
        Map<Long, TargetRow> rows = targets.list(m, credited).stream().collect(Collectors.toMap(TargetRow::repId, r -> r));
        List<AdminInsights.RepRow> out = new ArrayList<>();
        for (TargetRow r : rows.values()) {
            BigDecimal c = byRep.getOrDefault(r.repId(), InsightsQueries.money(null));
            out.add(new AdminInsights.RepRow(r.repId(), r.repName(), r.targetAmount(), r.commissionPct(), c,
                    Commission.progressPct(c, r.targetAmount()), Commission.hit(c, r.targetAmount()),
                    Commission.of(c, r.targetAmount(), r.commissionPct())));
        }
        BigDecimal none = byRep.get(null);
        if (none != null && none.signum() > 0) {
            out.add(new AdminInsights.RepRow(null, "Unassigned", null, null, none, null, false, Commission.ZERO));
        }
        out.sort(Comparator.comparing(AdminInsights.RepRow::collected).reversed()
                .thenComparing(r -> Objects.toString(r.name(), "")));
        return out;
    }
}
