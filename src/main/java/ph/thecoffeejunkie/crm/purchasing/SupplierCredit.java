package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;

/** Computed credit position; filled in by the credit service (null on responses until then). */
record SupplierCredit(BigDecimal balance, BigDecimal committed, BigDecimal available, String flag) {
}
