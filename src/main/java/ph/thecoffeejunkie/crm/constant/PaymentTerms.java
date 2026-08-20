package ph.thecoffeejunkie.crm.constant;

public enum PaymentTerms {

    DUE_ON_RECEIPT(0, "Due on Receipt"),
    NET_15(15, "Net 15"),
    NET_30(30, "Net 30"),
    NET_60(60, "Net 60");

    private final int days;
    private final String label;

    PaymentTerms(int days, String label) {
        this.days = days;
        this.label = label;
    }

    public int getDays() {
        return days;
    }

    public String getLabel() {
        return label;
    }
}
