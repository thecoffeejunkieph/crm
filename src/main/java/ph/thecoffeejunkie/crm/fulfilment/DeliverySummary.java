package ph.thecoffeejunkie.crm.fulfilment;

import java.time.Instant;

/** For the order page; proofUrl is the /api/v1/files path of the proof photo. */
public record DeliverySummary(long id, String number, DeliveryStatus status, DeliveryMethod method,
                              Instant dispatchedAt, Instant deliveredAt, String receiverName, String proofUrl) {
}
