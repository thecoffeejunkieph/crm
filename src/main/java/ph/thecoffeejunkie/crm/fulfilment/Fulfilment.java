package ph.thecoffeejunkie.crm.fulfilment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ph.thecoffeejunkie.crm.shared.HtmlMail;
import ph.thecoffeejunkie.crm.shared.Role;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What sales calls on release, revise and cancel. The caller owns the permission check. */
@Service
@Transactional
public class Fulfilment {

    private static final Logger log = LoggerFactory.getLogger(Fulfilment.class);

    private final DeliveryOrderRepository orders;
    private final DocumentCounters counters;
    private final AuditLog audit;
    private final Users users;
    private final Notifications notifications;
    private final StorageService storage;

    Fulfilment(DeliveryOrderRepository orders, DocumentCounters counters, AuditLog audit, Users users,
               Notifications notifications, StorageService storage) {
        this.storage = storage;
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

    /**
     * Locks the order's live delivery order, if any. Callers that will also lock the sales order take this first:
     * deliver locks the delivery order and then the sales order, so the reverse order could deadlock.
     */
    public void lockForOrder(long orderId) {
        orders.findBySalesOrderIdAndStatusNot(orderId, DeliveryStatus.CANCELLED);
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
            String title = "Delivery order " + d.number + " cancelled";
            String body = "Order " + d.orderNumber + " was changed or cancelled; stop picking.";
            notifications.toUsers(users.activeIdsWithRole(Role.ADMIN), NotificationType.RELEASED_ORDER_CHANGED,
                    "ORDER", orderId, title, body);
            notifications.toUsers(users.idsForWarehouse(d.warehouseId), NotificationType.RELEASED_ORDER_CHANGED,
                    DeliveryService.ENTITY, d.id, title, body);
        });
    }

    /** The order's live (non-cancelled) delivery order. */
    @Transactional(readOnly = true)
    public Optional<DeliverySummary> forOrder(long orderId) {
        return orders.readBySalesOrderIdAndStatusNot(orderId, DeliveryStatus.CANCELLED)
                .map(d -> new DeliverySummary(d.id, d.number, d.status, d.method, d.dispatchedAt, d.deliveredAt,
                        d.receiverName, DeliveryService.proofUrls(d)));
    }

    /** Raw bytes of proof photos one mail may carry; base64 adds a third, staying under common 25MB SMTP limits. */
    static final long MAIL_PHOTO_BUDGET = 15L * 1024 * 1024;

    /** What the customer status mails show; a photo that cannot be read, or would pass the budget, is left out. */
    @Transactional(readOnly = true)
    public Optional<DeliveryMailView> mailView(long deliveryOrderId) {
        return mailView(deliveryOrderId, MAIL_PHOTO_BUDGET);
    }

    // ponytail: whole photos only, no resizing; shrink them server-side if drivers' photos routinely pass the budget
    @Transactional(readOnly = true)
    Optional<DeliveryMailView> mailView(long deliveryOrderId, long budgetBytes) {
        return orders.findById(deliveryOrderId).map(d -> {
            List<HtmlMail.Attachment> photos = new ArrayList<>();
            long used = 0;
            int leftOut = 0;
            for (String key : d.proofKeys) {
                try {
                    // the key's extension was decided from the bytes when the photo was stored
                    String ext = key.substring(key.lastIndexOf('.') + 1);
                    byte[] bytes = storage.read(key);
                    if (used + bytes.length > budgetBytes) {
                        leftOut++;
                        continue;
                    }
                    used += bytes.length;
                    photos.add(new HtmlMail.Attachment(d.number + "-proof-" + (photos.size() + 1) + "." + ext,
                            "jpg".equals(ext) ? "image/jpeg" : "image/" + ext, bytes));
                } catch (RuntimeException e) {
                    log.warn("Proof photo {} of {} left out of the mail", key, d.number, e);
                }
            }
            return new DeliveryMailView(d.number, d.orderNumber, d.method, d.shipTo, d.receiverName, d.deliveredAt,
                    d.lines.stream().map(l -> new DeliveryMailView.Line(l.description, l.qty,
                            l.unit == null ? "" : l.unit.name())).toList(), photos, leftOut);
        });
    }
}
