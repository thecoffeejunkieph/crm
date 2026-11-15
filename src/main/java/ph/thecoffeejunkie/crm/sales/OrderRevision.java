package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** The order's state before a revision of a confirmed or released order. */
@Entity
@Table(name = "order_revision")
class OrderRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long orderId;
    int revision;
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> snapshot;
    String reason;
    Long createdBy;
    Instant createdAt = Instant.now();
}
