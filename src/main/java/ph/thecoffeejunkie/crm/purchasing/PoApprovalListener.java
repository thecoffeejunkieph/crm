package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.approvals.ApprovalDecided;
import ph.thecoffeejunkie.crm.approvals.ApprovalStatus;
import ph.thecoffeejunkie.crm.approvals.Approvals;

/** Sends or returns to draft a PO whose over-credit approval was decided. Idempotent: only PENDING_APPROVAL rows change. */
@Component
class PoApprovalListener {

    private final PurchaseOrderService orders;
    private final Approvals approvals;

    PoApprovalListener(PurchaseOrderService orders, Approvals approvals) {
        this.orders = orders;
        this.approvals = approvals;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ApprovalDecided e) {
        if (!PurchaseOrderService.ENTITY.equals(e.subjectType())
                || e.status() != ApprovalStatus.APPROVED && e.status() != ApprovalStatus.REJECTED) {
            return;
        }
        boolean approved = e.status() == ApprovalStatus.APPROVED;
        orders.decided(e.subjectId(), approved, approved ? null : approvals.decisionNote(e.approvalId()));
    }
}
