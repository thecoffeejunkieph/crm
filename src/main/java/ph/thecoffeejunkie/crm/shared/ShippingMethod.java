package ph.thecoffeejunkie.crm.shared;

/** The courier or arrangement a customer uses; recorded on the customer and carried onto delivery orders. */
public enum ShippingMethod {
    LALAMOVE("Lalamove"), CAPEX("CAPEX"), AP_CARGO("AP Cargo"), MORETA_SHIPPING_LINES("Moreta Shipping Lines"),
    DROP_AND_GO("Drop&Go"), SELF_PICKUP("Self Pickup");

    public final String label;

    ShippingMethod(String label) {
        this.label = label;
    }
}
