package ph.thecoffeejunkie.crm.fulfilment;

/** type is DELIVERY_ORDER, TRANSFER, PRODUCT or WAREHOUSE. */
public record ScanResult(String type, long id) {
}
