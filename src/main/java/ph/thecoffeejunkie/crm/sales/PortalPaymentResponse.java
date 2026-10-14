package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/** mine = submitted by this contact, so they may attach a 2307 to it. */
record PortalPaymentResponse(long id, PaymentVerification status, PaymentMethod method, BigDecimal cashAmount,
                             BigDecimal withholdingAmount, LocalDate receivedOn, String reference, String rejectReason,
                             boolean hasForm2307, boolean mine) {
}
