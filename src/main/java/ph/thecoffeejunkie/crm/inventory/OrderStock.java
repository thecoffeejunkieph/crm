package ph.thecoffeejunkie.crm.inventory;

import java.time.Instant;
import java.util.List;

public record OrderStock(long orderId, String orderNumber, long repUserId, long warehouseId, Instant confirmedAt,
                         List<ReserveLine> lines) {}
