package ph.thecoffeejunkie.crm.followups;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.sales.OrderConfirmed;
import ph.thecoffeejunkie.crm.sales.PaymentVerified;
import ph.thecoffeejunkie.crm.sales.QuoteClosed;

/** The events that answer open automatic follow-ups at once, without waiting for the daily scan's safety net. */
@Component
class AutoCloseListeners {

    private final JdbcTemplate jdbc;

    AutoCloseListeners(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A confirmed order answers the open RESTOCK and WIN_BACK follow-ups of its customer. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onOrderConfirmed(OrderConfirmed e) {
        jdbc.update("update follow_up f set done_at = now(), done_by = null, updated_at = now(), version = f.version + 1, "
                + "closed_reason = c.display_name || ' ordered ' || o.number "
                + "from customer c, sales_order o where c.id = f.customer_id and o.id = ? "
                + "and f.customer_id = ? and f.done_at is null and f.rule in ('RESTOCK', 'WIN_BACK')",
                e.orderId(), e.customerId());
    }

    /** A quote answered, revised or cancelled ends its chase and expiry reminders; a cancelled order owes nothing. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onQuoteClosed(QuoteClosed e) {
        jdbc.update("update follow_up f set done_at = now(), done_by = null, updated_at = now(), version = f.version + 1, "
                + "closed_reason = 'Quote ' || o.number || ' ' || ?::text from sales_order o where o.id = ? "
                + "and f.done_at is null and (starts_with(f.auto_key, 'QUOTE_CHASE:order:' || o.id || ':') "
                + "or starts_with(f.auto_key, 'QUOTE_EXPIRING:order:' || o.id || ':'))", e.outcome(), e.orderId());
        if ("cancelled".equals(e.outcome())) {
            closePayment(e.orderId(), "Order cancelled", false);
        }
    }

    /** A verified payment that clears the balance ends the overdue follow-up. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onPaymentVerified(PaymentVerified e) {
        closePayment(e.orderId(), "Paid in full", true);
    }

    private void closePayment(long orderId, String reason, boolean onlyIfSettled) {
        jdbc.update("update follow_up f set done_at = now(), done_by = null, closed_reason = ?, updated_at = now(), "
                + "version = f.version + 1 from sales_order o where o.id = ? and f.auto_key = 'PAYMENT_OVERDUE:order:' || o.id "
                + "and f.done_at is null and (?::boolean = false or o.total - o.settled_amount <= 0)", reason, orderId, onlyIfSettled);
    }
}
