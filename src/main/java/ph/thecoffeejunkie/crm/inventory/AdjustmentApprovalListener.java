package ph.thecoffeejunkie.crm.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.approvals.ApprovalDecided;
import ph.thecoffeejunkie.crm.approvals.ApprovalStatus;

/** Posts or rejects a pending write-off once its approval is decided. Idempotent: only PENDING_APPROVAL rows change. */
@Component
class AdjustmentApprovalListener {

    private final AdjustmentService adjustments;

    AdjustmentApprovalListener(AdjustmentService adjustments) {
        this.adjustments = adjustments;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ApprovalDecided e) {
        if (!AdjustmentService.ENTITY.equals(e.subjectType())
                || e.status() != ApprovalStatus.APPROVED && e.status() != ApprovalStatus.REJECTED) {
            return;
        }
        adjustments.decided(e.subjectId(), e.status() == ApprovalStatus.APPROVED);
    }
}
