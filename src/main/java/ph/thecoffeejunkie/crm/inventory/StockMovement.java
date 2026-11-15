package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Append-only: crm_app has no UPDATE or DELETE on stock_movement. */
@Entity
@Table(name = "stock_movement")
class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long productId;
    long warehouseId;
    @Enumerated(EnumType.STRING)
    MovementType type;
    BigDecimal qty;
    String refType;
    Long refId;
    String reason;
    Long createdBy;
    Instant createdAt = Instant.now();
}
