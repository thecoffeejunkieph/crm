package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record MovementResponse(long id, MovementType type, BigDecimal qty, String refType, long refId,
                               String refNumber, String reason, String createdByName, Instant createdAt) {
}
