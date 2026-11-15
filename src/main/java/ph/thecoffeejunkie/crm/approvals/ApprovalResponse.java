package ph.thecoffeejunkie.crm.approvals;

import java.time.Instant;
import java.util.Map;

public record ApprovalResponse(long id, ApprovalType type, String subjectType, long subjectId, long subjectVersion,
                               Map<String, Object> requestedValue, String reason, ApprovalStatus status,
                               long requestedById, String requestedByName, Instant requestedAt, Long decidedById,
                               String decidedByName, Instant decidedAt, String decisionNote) {
}
