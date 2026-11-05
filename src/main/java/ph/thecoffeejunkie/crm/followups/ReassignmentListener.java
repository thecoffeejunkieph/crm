package ph.thecoffeejunkie.crm.followups;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.CustomerReassigned;

/** Open follow-ups follow the customer to its new rep, in the same transaction as the assignment. */
@Component
class ReassignmentListener {

    private final FollowUpService followUps;

    ReassignmentListener(FollowUpService followUps) {
        this.followUps = followUps;
    }

    @EventListener
    void on(CustomerReassigned e) {
        // Unassigned: nobody to hand them to, so the old rep keeps them.
        if (e.fromRepId() != null && e.toRepId() != null) {
            followUps.moveOpen(e.customerId(), e.fromRepId(), e.toRepId());
        }
    }
}
