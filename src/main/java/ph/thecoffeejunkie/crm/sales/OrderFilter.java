package ph.thecoffeejunkie.crm.sales;

import java.util.List;

/** awaitingConfirmation = QUOTED and accepted by the customer. */
record OrderFilter(List<OrderStatus> status, PaymentStatus paymentStatus, Long customerId, Long repId, String q,
                   boolean awaitingConfirmation) {
}
