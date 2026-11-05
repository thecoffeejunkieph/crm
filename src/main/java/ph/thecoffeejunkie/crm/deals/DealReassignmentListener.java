package ph.thecoffeejunkie.crm.deals;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.CustomerReassigned;

/** Open deals follow the customer to its new rep, in the same transaction as the assignment. */
@Component
class DealReassignmentListener {

    private final DealService deals;

    DealReassignmentListener(DealService deals) {
        this.deals = deals;
    }

    @EventListener
    void on(CustomerReassigned e) {
        // Unassigned: nobody to hand them to, so the old rep keeps them until the next assignment.
        if (e.toRepId() != null) {
            deals.moveOpen(e.customerId(), e.toRepId());
        }
    }
}
