package ph.thecoffeejunkie.crm.followups;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** How many days after a delivery a customer usually needs to reorder, from its own recent rhythm. */
public final class RestockRhythm {

    public static final int MAX_DATES = 6; // 5 gaps

    private RestockRhythm() {
    }

    /** {@code deliveriesNewestFirst}: the customer's delivered-order dates. */
    public static int dueAfterDays(List<LocalDate> deliveriesNewestFirst, RuleSettings s) {
        List<LocalDate> d = deliveriesNewestFirst.stream().limit(MAX_DATES).toList();
        if (d.size() < 2) {
            return s.restockFallbackDays();
        }
        long total = ChronoUnit.DAYS.between(d.getLast(), d.getFirst());
        int gaps = d.size() - 1;
        int scaled = BigDecimal.valueOf(total * s.restockRhythmPercent())
                .divide(BigDecimal.valueOf(gaps * 100L), 0, RoundingMode.HALF_UP).intValueExact();
        return Math.max(s.restockMinDays(), scaled);
    }
}
