package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Changed only while the matching stock_level row is locked, so it needs no lock of its own. */
@Entity
@Table(name = "stock_reservation")
class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long orderLineId;
    long salesOrderId;
    String orderNumber;
    Long repUserId;
    long productId;
    long warehouseId;
    BigDecimal reservedQty;
    BigDecimal shortQty;
    Instant confirmedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
