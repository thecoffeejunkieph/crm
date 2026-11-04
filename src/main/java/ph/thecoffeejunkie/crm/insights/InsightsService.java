package ph.thecoffeejunkie.crm.insights;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

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
}
