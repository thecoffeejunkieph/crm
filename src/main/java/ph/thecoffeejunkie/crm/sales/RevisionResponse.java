package ph.thecoffeejunkie.crm.sales;

import java.time.Instant;
import java.util.Map;

/** snapshot: paymentTerms, shippingFee, notes, terms, lines[], totals - the order as it was before the revision. */
record RevisionResponse(int revision, Map<String, Object> snapshot, String reason, String createdByName,
                        Instant createdAt) {
}
