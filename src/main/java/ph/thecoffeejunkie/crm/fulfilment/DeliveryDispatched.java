package ph.thecoffeejunkie.crm.fulfilment;

/** Published in the dispatch transaction: the delivery order has left the warehouse. */
public record DeliveryDispatched(long deliveryOrderId, long salesOrderId) {
}
