package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Commission is earned only once the month's target is hit, on everything collected (spec 2026-11-05). */
final class Commission {

    private static final BigDecimal HUNDRED = new BigDecimal(100);
    static final BigDecimal ZERO = new BigDecimal("0.00");

    private Commission() {
    }

    static BigDecimal of(BigDecimal collected, BigDecimal target, BigDecimal pct) {
        if (!hit(collected, target) || pct == null) {
            return ZERO;
        }
        return collected.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    static boolean hit(BigDecimal collected, BigDecimal target) {
        return target != null && target.signum() > 0 && collected.compareTo(target) >= 0;
    }

    /** Floor of collected / target × 100, not capped; null without a positive target. */
    static Integer progressPct(BigDecimal collected, BigDecimal target) {
        if (target == null || target.signum() <= 0) {
            return null;
        }
        return collected.multiply(HUNDRED).divide(target, 0, RoundingMode.FLOOR).intValueExact();
    }

    /** What is left to reach the target, never negative; null without a target. */
    static BigDecimal remaining(BigDecimal collected, BigDecimal target) {
        return target == null ? null : target.subtract(collected).max(ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
