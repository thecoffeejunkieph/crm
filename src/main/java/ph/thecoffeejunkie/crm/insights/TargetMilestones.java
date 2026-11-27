package ph.thecoffeejunkie.crm.insights;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.sales.PaymentVerified;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tells a rep when a verified payment carries them past 50%, 80% or 100% of the month's target, in the verifying
 * transaction. Reads the queries directly: the dashboard figures are cached for 60s.
 */
@Component
class TargetMilestones {

    private static final int[] THRESHOLDS = {100, 80, 50};

    private final InsightsQueries q;
    private final TargetService targets;
    private final Notifications notifications;

    TargetMilestones(InsightsQueries q, TargetService targets, Notifications notifications) {
        this.q = q;
        this.targets = targets;
        this.notifications = notifications;
    }

    @EventListener
    public void on(PaymentVerified e) {
        if (e.repId() == null) {
            return;
        }
        YearMonth m = YearMonth.from(e.verifiedAt().atZone(InsightsMonth.MANILA));
        String month = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        overall(e, m, month);
        categories(e, m, month);
    }

    private void overall(PaymentVerified e, YearMonth m, String month) {
        BigDecimal target = targets.find(e.repId(), m).map(t -> t.targetAmount).orElse(null);
        if (target == null || target.signum() <= 0) {
            return;
        }
        BigDecimal after = q.netSales(e.repId(), m);
        Integer t = crossed(Commission.progressPct(q.netSales(e.repId(), m, e.paymentId()), target),
                Commission.progressPct(after, target));
        if (t == null) {
            return;
        }
        String title = t == 100 ? "Target hit for " + month : "You're at " + t + "% of your " + month + " target";
        send(e, title, after, target);
    }

    private void categories(PaymentVerified e, YearMonth m, String month) {
        List<CategoryTargetRow> rows = targets.categoryTargets(e.repId(), m).stream()
                .filter(c -> c.targetAmount() != null && c.targetAmount().signum() > 0).toList();
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> before = q.netSalesByCategory(e.repId(), m, e.paymentId());
        Map<Long, BigDecimal> after = q.netSalesByCategory(e.repId(), m, null);
        for (CategoryTargetRow c : rows) {
            BigDecimal now = after.getOrDefault(c.categoryId(), Commission.ZERO);
            Integer t = crossed(Commission.progressPct(before.getOrDefault(c.categoryId(), Commission.ZERO), c.targetAmount()),
                    Commission.progressPct(now, c.targetAmount()));
            if (t == null) {
                continue;
            }
            String title = t == 100 ? c.categoryName() + " target hit for " + month
                    : "You're at " + t + "% of your " + c.categoryName() + " target for " + month;
            send(e, title, now, c.targetAmount());
        }
    }

    private void send(PaymentVerified e, String title, BigDecimal after, BigDecimal target) {
        notifications.toUser(e.repId(), NotificationType.TARGET_MILESTONE, "SALES_TARGET", null, title,
                after.toPlainString() + " of " + target.toPlainString());
    }

    /** The highest threshold with before < t <= after, or null. */
    static Integer crossed(Integer before, Integer after) {
        for (int t : THRESHOLDS) {
            if (before < t && t <= after) {
                return t;
            }
        }
        return null;
    }
}
