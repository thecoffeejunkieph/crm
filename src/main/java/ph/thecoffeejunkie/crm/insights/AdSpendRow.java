package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;

/** One source's spend for a month; {@code amount} is null when none was entered. */
record AdSpendRow(long sourceId, String sourceName, boolean archived, BigDecimal amount) {
}
