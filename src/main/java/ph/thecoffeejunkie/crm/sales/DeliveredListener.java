package ph.thecoffeejunkie.crm.sales;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.fulfilment.OrderDelivered;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;

/** A delivered delivery order fulfils its order, in the deliver transaction. Only a RELEASED order moves. */
@Component
class DeliveredListener {

    private final SalesOrderRepository repo;
    private final AuditLog audit;
    private final Notifications notifications;

    DeliveredListener(SalesOrderRepository repo, AuditLog audit, Notifications notifications) {
        this.repo = repo;
        this.audit = audit;
        this.notifications = notifications;
    }

    @EventListener
    public void on(OrderDelivered e) {
        SalesOrder o = repo.findByIdForUpdate(e.orderId()).orElse(null);
        if (o == null || o.status != OrderStatus.RELEASED) {
            return;
        }
        o.status = OrderStatus.FULFILLED;
        o.fulfilledAt = e.deliveredAt();
        o.updatedAt = e.deliveredAt();
        repo.saveAndFlush(o);
        audit.record(AuditAction.FULFILLED, OrderService.ENTITY, o.id, "Order " + o.number + " delivered",
                AuditChanges.diff().field("status", OrderStatus.RELEASED, OrderStatus.FULFILLED));
        notifications.toUser(OrderStockMapper.rep(o), NotificationType.ORDER_DELIVERED, OrderService.ENTITY, o.id,
                "Order " + o.number + " delivered", null);
    }
}
