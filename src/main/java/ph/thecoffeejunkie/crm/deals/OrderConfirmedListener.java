package ph.thecoffeejunkie.crm.deals;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.sales.OrderConfirmed;

/** Same transaction as the confirm: the deal is won together with the order, or neither. */
@Component
class OrderConfirmedListener {

    private final DealService deals;

    OrderConfirmedListener(DealService deals) {
        this.deals = deals;
    }

    @EventListener
    void on(OrderConfirmed e) {
        deals.orderConfirmed(e.orderId());
    }
}
