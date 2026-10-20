package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Written only by {@link StockLedger}. */
@Entity
@Table(name = "stock_level")
class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long productId;
    long warehouseId;
    BigDecimal onHand;
    BigDecimal reserved;
    Instant createdAt;
    Instant updatedAt;
    @Version
    long version;

    BigDecimal available() {
        return onHand.subtract(reserved);
    }
}
