package ph.thecoffeejunkie.crm.approvals;

/** Published inside {@code decide}; listeners that act on it should run after commit. */
public record ApprovalDecided(long approvalId, ApprovalType type, String subjectType, long subjectId,
                              long subjectVersion, ApprovalStatus status) {
}
