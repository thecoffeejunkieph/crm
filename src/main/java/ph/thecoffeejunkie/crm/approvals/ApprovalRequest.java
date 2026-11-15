package ph.thecoffeejunkie.crm.approvals;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "approval_request")
class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Enumerated(EnumType.STRING)
    ApprovalType type;
    String subjectType;
    long subjectId;
    long subjectVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> requestedValue;
    String reason;
    @Enumerated(EnumType.STRING)
    ApprovalStatus status = ApprovalStatus.PENDING;
    long requestedBy;
    Instant requestedAt = Instant.now();
    Long decidedBy;
    Instant decidedAt;
    String decisionNote;
    /** Guards decide/cancel races: the loser of two concurrent decisions gets 409 STALE_VERSION. */
    @Version
    long version;

    ApprovalResponse toResponse(Map<Long, String> names) {
        return new ApprovalResponse(id, type, subjectType, subjectId, subjectVersion, requestedValue, reason, status,
                requestedBy, names.get(requestedBy), requestedAt, decidedBy,
                decidedBy == null ? null : names.get(decidedBy), decidedAt, decisionNote);
    }
}
