package ph.thecoffeejunkie.crm.warehouses;

public record WarehouseResponse(long id, String code, String name, String address, boolean isDefault, long version) {
}
