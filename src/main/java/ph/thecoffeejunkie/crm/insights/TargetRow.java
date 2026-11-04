package ph.thecoffeejunkie.crm.insights;

import java.io.Serializable;
import java.math.BigDecimal;

/** One SALES user's target for a month; amount, rate and version are null when none is set. */
record TargetRow(long repId, String repName, BigDecimal targetAmount, BigDecimal commissionPct, Long version)
        implements Serializable {
}
