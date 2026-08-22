package ph.thecoffeejunkie.crm.constant;

public enum PaymentMethod {

    CASH("Cash"),
    GCASH("GCash"),
    BANK_TRANSFER("Bank Transfer"),
    CREDIT_CARD("Credit Card");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
