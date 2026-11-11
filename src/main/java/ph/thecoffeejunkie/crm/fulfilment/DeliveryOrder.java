package ph.thecoffeejunkie.crm.fulfilment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "delivery_order")
class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    long salesOrderId;
    String orderNumber;
    long repUserId;
    long warehouseId;
    @Enumerated(EnumType.STRING)
    DeliveryMethod method;
    @Enumerated(EnumType.STRING)
    DeliveryStatus status = DeliveryStatus.PICKING;
    Instant dispatchedAt;
    Long dispatchedBy;
    Instant deliveredAt;
    Long deliveredBy;
    @JdbcTypeCode(SqlTypes.ARRAY)
    List<String> proofKeys = new ArrayList<>();
    String receiverName;
    String customerName;
    String shipTo;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
    @OneToMany(mappedBy = "deliveryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    List<DeliveryOrderLine> lines = new ArrayList<>();
}
