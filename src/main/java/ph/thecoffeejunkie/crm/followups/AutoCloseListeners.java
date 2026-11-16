package ph.thecoffeejunkie.crm.followups;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ph.thecoffeejunkie.crm.sales.OrderConfirmed;

/** A confirmed order answers the open RESTOCK and WIN_BACK follow-ups of its customer. */
@Component
class AutoCloseListeners {

    private final JdbcTemplate jdbc;

    AutoCloseListeners(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onOrderConfirmed(OrderConfirmed e) {
        jdbc.update("update follow_up f set done_at = now(), done_by = null, updated_at = now(), version = f.version + 1, "
                + "closed_reason = c.display_name || ' ordered ' || o.number "
                + "from customer c, sales_order o where c.id = f.customer_id and o.id = ? "
                + "and f.customer_id = ? and f.done_at is null and f.rule in ('RESTOCK', 'WIN_BACK')",
                e.orderId(), e.customerId());
    }
}
