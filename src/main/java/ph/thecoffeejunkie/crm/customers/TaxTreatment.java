package ph.thecoffeejunkie.crm.customers;

/** Anything but REGULAR overrides every product tax class on the customer's orders. */
public enum TaxTreatment {
    REGULAR, ZERO_RATED, VAT_EXEMPT
}
