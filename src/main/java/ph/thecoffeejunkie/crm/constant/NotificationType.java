package ph.thecoffeejunkie.crm.constant;

/** What a notification is about; the type also tells the frontend which record entityId points at. */
public enum NotificationType {
    QUOTATION_ACCEPTED,
    QUOTATION_REJECTED,
    QUOTATION_EXPIRED,
    PAYMENT_AWAITING_VERIFICATION,
    INVOICE_PAID,
    DELIVERY_ORDER_CREATED,
    DELIVERY_ORDER_DELIVERED
}
