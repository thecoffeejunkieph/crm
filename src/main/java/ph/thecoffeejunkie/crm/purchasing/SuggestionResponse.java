package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;
import java.util.List;

/** Reorder suggestions grouped by preferred supplier; a null supplierId is the "no supplier" group. */
record SuggestionResponse(List<SuggestionGroup> groups) {

    record SuggestionGroup(Long supplierId, String supplierName, Integer leadTimeDays, List<SuggestionItem> items) {
    }

    /** perDay and coverDays are hints; coverDays is null when nothing shipped in the last 30 days. */
    record SuggestionItem(long productId, String sku, String name, String unit, BigDecimal available,
                          BigDecimal backorder, BigDecimal incoming, BigDecimal reorderLevel, BigDecimal reorderTarget,
                          BigDecimal suggestedQty, BigDecimal perDay, BigDecimal coverDays, String onDraftPo) {
    }
}
