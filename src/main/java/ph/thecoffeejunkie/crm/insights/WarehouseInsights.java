package ph.thecoffeejunkie.crm.insights;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Today's work in the caller's warehouses. No money anywhere. */
record WarehouseInsights(Today today, Transfers transfers, List<DeliveryRef> queue) implements Serializable {

    record Today(long toPick, long dispatched, long deliveredToday) implements Serializable {
    }

    /** {@code toDispatch}: drafts leaving my warehouses; {@code incoming}: dispatched to them. */
    record Transfers(long toDispatch, long incoming) implements Serializable {
    }

    record DeliveryRef(long id, String number, String customerName, String status, Instant since) implements Serializable {
    }
}
