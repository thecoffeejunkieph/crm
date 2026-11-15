package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** File URLs are short-lived presigned links; null when there is no file. */
record PaymentResponse(long id, PaymentVerification status, PaymentMethod method, BigDecimal cashAmount,
                       BigDecimal withholdingAmount, LocalDate receivedOn, String reference, String proofUrl,
                       String form2307Url, String submittedByType, String submittedByName, Instant verifiedAt,
                       String rejectReason, Instant createdAt, long version) {
}
