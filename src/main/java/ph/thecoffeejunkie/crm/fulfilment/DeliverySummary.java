package ph.thecoffeejunkie.crm.fulfilment;

import java.time.Instant;

import java.util.List;

/** For the order page; proofUrls are the /api/v1/files paths of the proof photos. */
public record DeliverySummary(long id, String number, DeliveryStatus status, DeliveryMethod method,
                              Instant dispatchedAt, Instant deliveredAt, String receiverName, List<String> proofUrls) {
}
