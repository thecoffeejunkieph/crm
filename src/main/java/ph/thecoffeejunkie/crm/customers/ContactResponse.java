package ph.thecoffeejunkie.crm.customers;

public record ContactResponse(long id, String name, String email, String phone, boolean portalEnabled, long version) {
}
