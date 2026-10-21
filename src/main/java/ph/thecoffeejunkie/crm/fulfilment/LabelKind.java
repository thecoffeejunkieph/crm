package ph.thecoffeejunkie.crm.fulfilment;

/** The four things a printed code can point at: the QR text is crm:{name}:{id}. */
enum LabelKind {
    DO("DELIVERY_ORDER"), TR("TRANSFER"), PR("PRODUCT"), WH("WAREHOUSE");

    final String type;

    LabelKind(String type) {
        this.type = type;
    }
}
