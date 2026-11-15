package ph.thecoffeejunkie.crm.insights;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.sales.PaymentVerified;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

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
        String month = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String title = t == 100 ? "Target hit for " + month : "You're at " + t + "% of your " + month + " target";
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
