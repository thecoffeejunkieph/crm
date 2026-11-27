package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;

/** Computed credit position. {@code available} and {@code flag} are null when there is no limit; flag is OVER, NEAR or null. */
record SupplierCredit(BigDecimal limit, BigDecimal balance, BigDecimal committed, BigDecimal available, String flag) {
}
