package ph.thecoffeejunkie.crm.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.approvals.ApprovalDecided;
import ph.thecoffeejunkie.crm.approvals.ApprovalStatus;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.DomainException;

/**
 * Retries confirm for a customer-accepted quote when one of its approvals is granted. Idempotent: does nothing
 * unless the order is still QUOTED and accepted, and the approval is for its current content.
 */
@Component
class AutoConfirmListener {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmListener.class);

    private final SalesOrderRepository repo;
    private final OrderService orders;
    private final Notifications notifications;

    AutoConfirmListener(SalesOrderRepository repo, OrderService orders, Notifications notifications) {
        this.repo = repo;
        this.orders = orders;
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ApprovalDecided e) {
        if (!ApprovalChecks.ORDER.equals(e.subjectType()) || e.status() != ApprovalStatus.APPROVED) {
            return;
        }
        SalesOrder o = repo.findByIdForUpdate(e.subjectId()).orElse(null);
        if (o == null || o.status != OrderStatus.QUOTED || o.customerAcceptedAt == null
                || e.subjectVersion() != OrderFingerprint.of(o)) {
            return;
        }
        try {
            if (orders.confirmAsSystem(o.id).isEmpty()) {
                notifications.toUser(o.repId != null ? o.repId : o.createdBy, NotificationType.ORDER_CONFIRMED,
                        OrderService.ENTITY, o.id, "Order " + o.number + " confirmed",
                        "All approvals are in; the customer-accepted quote is now confirmed.");
            }
        } catch (DomainException ex) {
            log.info("Order {} not auto-confirmed: {}", o.id, ex.getMessage());
        }
    }
}
