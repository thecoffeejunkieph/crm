package ph.thecoffeejunkie.crm.approvals;

import java.util.Map;

/** One approval a guarded action needs: an APPROVED request with the same type and subject satisfies it. */
public record RequiredApproval(ApprovalType type, String subjectType, long subjectId, long subjectVersion,
                               Map<String, Object> requestedValue) {
}
