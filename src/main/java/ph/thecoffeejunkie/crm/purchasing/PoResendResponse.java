package ph.thecoffeejunkie.crm.purchasing;

import java.util.List;

/** Addresses the PO was re-emailed to; empty when the supplier has no email. */
record PoResendResponse(List<String> to) {
}
