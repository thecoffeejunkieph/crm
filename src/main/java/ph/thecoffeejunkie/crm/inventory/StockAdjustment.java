package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_adjustment")
class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    long warehouseId;
    long productId;
    @Enumerated(EnumType.STRING)
    AdjustmentMode mode;
    BigDecimal countedQty;
    BigDecimal delta;
    String reason;
    @Enumerated(EnumType.STRING)
    AdjustmentSource source = AdjustmentSource.MANUAL;
    @Enumerated(EnumType.STRING)
    AdjustmentStatus status;
    Long approvalRequestId;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
