package ph.thecoffeejunkie.crm.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Insert-only: the app DB role has no UPDATE/DELETE on audit_event. */
@Entity
@Immutable
@Table(name = "audit_event")
class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Instant at = Instant.now();
    String actorType;
    Long actorId;
    String actorName;
    String action;
    String entityType;
    Long entityId;
    String summary;
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, List<Object>> changes;
    String ip;
    String requestId;

    AuditEventResponse toResponse() {
        return new AuditEventResponse(id, at, actorType, actorId, actorName, action, entityType, entityId, summary,
                changes);
    }
}
