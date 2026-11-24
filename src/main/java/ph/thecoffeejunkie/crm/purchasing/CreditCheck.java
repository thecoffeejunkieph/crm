package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;

/** Credit position if this PO were sent now. {@code limit} is null when the supplier has none. */
record CreditCheck(BigDecimal exposure, BigDecimal limit, BigDecimal over, boolean needsApproval) {
}
