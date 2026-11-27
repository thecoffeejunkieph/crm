package ph.thecoffeejunkie.crm.insights;

import java.io.Serializable;
import java.math.BigDecimal;

/** One category's target for a rep and month; amount and version are null when none is set. */
record CategoryTargetRow(long categoryId, String categoryName, boolean archived, BigDecimal targetAmount, Long version)
        implements Serializable {
}
