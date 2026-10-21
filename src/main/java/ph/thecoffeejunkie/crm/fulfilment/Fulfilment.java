package ph.thecoffeejunkie.crm.fulfilment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.notifications.NotificationType;
import ph.thecoffeejunkie.crm.notifications.Notifications;
import ph.thecoffeejunkie.crm.shared.DocumentCounters;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/** What sales calls on release, revise and cancel. The caller owns the permission check. */
@Service
@Transactional
public class Fulfilment {

    private final DeliveryOrderRepository orders;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final Users users;
    private final Notifications notifications;

    Fulfilment(DeliveryOrderRepository orders, DocumentCounters counters, AuditLog audit, Users users,
               Notifications notifications) {
        this.orders = orders;
        this.counters = counters;
        this.audit = audit;
        this.users = users;
        this.notifications = notifications;
    }

    /** A PICKING delivery order with one line per order line; the warehouse's users are told. */
    public long create(DeliveryRequest r) {
        DeliveryOrder d = new DeliveryOrder();
        d.number = counters.format("DO", "DELIVERY_ORDER");
        d.salesOrderId = r.orderId();
        d.orderNumber = r.orderNumber();
        d.repUserId = r.repUserId();
        d.warehouseId = r.warehouseId();
        d.method = r.method();
        d.customerName = r.customerName();
        d.shipTo = r.shipTo();
        for (DeliveryRequest.DeliveryLine l : r.lines()) {
            DeliveryOrderLine line = new DeliveryOrderLine();
            line.deliveryOrder = d;
            line.orderLineId = l.orderLineId();
            line.productId = l.productId();
            line.description = l.description();
            line.unit = l.unit();
            line.qty = l.qty().setScale(3, RoundingMode.UNNECESSARY);
            d.lines.add(line);
        }
        orders.saveAndFlush(d);
        audit.record(AuditAction.DELIVERY_CREATED, DeliveryService.ENTITY, d.id,
                "Created delivery order " + d.number + " for " + d.orderNumber, null);
        for (long userId : users.idsForWarehouse(d.warehouseId)) {
            notifications.toUser(userId, NotificationType.DELIVERY_CREATED, DeliveryService.ENTITY, d.id,
                    "Delivery order " + d.number + " is ready to pick",
                    d.orderNumber + " for " + d.customerName + ", " + d.lines.size() + " line(s).");
        }
        return d.id;
    }

    /** PICKING becomes CANCELLED; once it has left the warehouse it cannot be. No delivery order is a no-op. */
    public void cancelForOrder(long orderId) {
        orders.findBySalesOrderIdAndStatusNot(orderId, DeliveryStatus.CANCELLED).ifPresent(d -> {
            if (d.status != DeliveryStatus.PICKING) {
                throw new DomainException(ErrorCode.INVALID_STATE, "This order has already left the warehouse");
            }
            d.status = DeliveryStatus.CANCELLED;
            d.updatedAt = Instant.now();
            audit.record(AuditAction.DELIVERY_CANCELLED, DeliveryService.ENTITY, d.id,
                    "Cancelled delivery order " + d.number, null);
        });
    }

    /** The order's live (non-cancelled) delivery order. */
    @Transactional(readOnly = true)
    public Optional<DeliverySummary> forOrder(long orderId) {
        return orders.readBySalesOrderIdAndStatusNot(orderId, DeliveryStatus.CANCELLED)
                .map(d -> new DeliverySummary(d.id, d.number, d.status, d.method, d.dispatchedAt, d.deliveredAt,
                        d.receiverName, StorageService.url(d.proofKey)));
    }
}
