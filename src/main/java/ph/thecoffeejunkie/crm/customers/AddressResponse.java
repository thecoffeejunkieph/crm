package ph.thecoffeejunkie.crm.customers;

public record AddressResponse(long id, String label, String address, boolean defaultAddress, long version) {
}