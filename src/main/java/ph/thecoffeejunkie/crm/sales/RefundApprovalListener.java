package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.approvals.ApprovalDecided;
import ph.thecoffeejunkie.crm.approvals.ApprovalStatus;

/** Approves or rejects a requested refund once its approval is decided. Idempotent: only REQUESTED rows change. */
@Component
class RefundApprovalListener {

    private final RefundService refunds;

    RefundApprovalListener(RefundService refunds) {
        this.refunds = refunds;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ApprovalDecided e) {
        if (!RefundService.ENTITY.equals(e.subjectType())
                || e.status() != ApprovalStatus.APPROVED && e.status() != ApprovalStatus.REJECTED) {
            return;
        }
        refunds.decided(e.subjectId(), e.status() == ApprovalStatus.APPROVED);
    }
}
