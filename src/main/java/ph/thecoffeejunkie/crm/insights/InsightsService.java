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
    /** Dashboard lists show 5 at a time; the UI reveals the rest with "See more". */
    private static final int TOP = 20;


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
        BigDecimal collected = q.collected(userId, Period.of(m));
        BigDecimal net = q.netSales(userId, m);
        SalesInsights.Target target = targets.find(userId, m)
                .map(t -> new SalesInsights.Target(t.targetAmount, t.commissionPct)).orElse(null);
        BigDecimal amount = target == null ? null : target.amount();
        BigDecimal pct = target == null ? null : target.commissionPct();
        InsightsQueries.Balance owed = q.outstanding(userId, InsightsMonth.today(clock));
        return new SalesInsights(m, target, collected, net, Commission.progressPct(net, amount),
                Commission.remaining(net, amount), Commission.hit(net, amount),
                Commission.of(net, amount, pct), q.invoiced(userId, Period.of(m)), owed.outstanding(), owed.overdue(),
                q.toVerify(userId), q.collectedByDay(userId, m),
                q.needsAction(userId, InsightsMonth.today(clock), 5), q.topCustomers(userId, Period.of(m), TOP));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "insights", key = "'admin:' + #p")
    public AdminInsights admin(Period p) {
        BigDecimal collected = q.collected(null, p);
        LocalDate today = InsightsMonth.today(clock);
        InsightsQueries.Balance owed = q.outstanding(null, today);
        return new AdminInsights(YearMonth.from(p.from()), p.unit(), p.from(), p.to().minusDays(1), collected,
                q.collected(null, p.previous()), q.invoiced(null, p), q.margin(p), owed.outstanding(), owed.overdue(),
                q.toVerify(null), q.collectedTrend(p), reps(p),
                q.topProducts(p, TOP), q.topCustomers(null, p, TOP), q.operations());
    }

    /** {@code warehouseIds} null for ADMIN (every warehouse). The key sorts the ids so set order never splits it. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "insights",
            key = "'wh:' + (#warehouseIds == null ? 'all' : new java.util.TreeSet(#warehouseIds)) + ':' + #today")
    public WarehouseInsights warehouse(Set<Long> warehouseIds, LocalDate today) {
        return q.warehouse(warehouseIds, today);
    }

    /** Every active SALES user (zeros included), anyone credited or targeted that month, then "Unassigned" when it collected anything; by collected desc. */
    private List<AdminInsights.RepRow> reps(Period p) {
        Map<Long, InsightsQueries.RepAmounts> byRep = q.collectedByRep(p);
        // Targets are monthly: a week or year shows reps' sales without a target.
        boolean monthly = p.unit().equals("month");
        YearMonth m = YearMonth.from(p.from());
        InsightsQueries.RepAmounts zero = new InsightsQueries.RepAmounts(InsightsQueries.money(null), InsightsQueries.money(null));
        List<Long> credited = byRep.keySet().stream().filter(Objects::nonNull).toList();
        Map<Long, TargetRow> rows = targets.list(m, credited).stream().collect(Collectors.toMap(TargetRow::repId, r -> r));
        List<AdminInsights.RepRow> out = new ArrayList<>();
        for (TargetRow r : rows.values()) {
            InsightsQueries.RepAmounts a = byRep.getOrDefault(r.repId(), zero);
            BigDecimal c = a.netSales();
            BigDecimal target = monthly ? r.targetAmount() : null;
            BigDecimal pct = monthly ? r.commissionPct() : null;
            out.add(new AdminInsights.RepRow(r.repId(), r.repName(), target, pct, a.collected(), c,
                    Commission.progressPct(c, target), Commission.hit(c, target), Commission.of(c, target, pct)));
        }
        InsightsQueries.RepAmounts none = byRep.get(null);
        if (none != null && none.collected().signum() > 0) {
            out.add(new AdminInsights.RepRow(null, "Unassigned", null, null, none.collected(), none.netSales(), null, false,
                    Commission.ZERO));
        }
        out.sort(Comparator.comparing(AdminInsights.RepRow::collected).reversed()
                .thenComparing(r -> Objects.toString(r.name(), "")));
        return out;
    }
}
