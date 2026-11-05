package ph.thecoffeejunkie.crm.deals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/** A quote or order linked to a deal. An order is on at most one deal (unique index). */
@Entity
@Table(name = "deal_order")
@IdClass(DealOrder.Key.class)
class DealOrder {

    record Key(Long dealId, Long orderId) implements Serializable {
        Key() {
            this(null, null);
        }
    }

    @Id
    Long dealId;
    @Id
    Long orderId;
    Instant linkedAt = Instant.now();
    Long linkedBy;
}
