package ph.thecoffeejunkie.crm.purchasing;

/** Just enough to pick a supplier, for roles that may not read supplier finances (e.g. warehouse stock in). */
public record SupplierOption(long id, String code, String name) {
}
