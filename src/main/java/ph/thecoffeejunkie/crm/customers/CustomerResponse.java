package ph.thecoffeejunkie.crm.customers;

import ph.thecoffeejunkie.crm.shared.ShippingMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerResponse(long id, CustomerType type, String displayName, String businessName, String tin,
                               String address, String phone, String email, BigDecimal creditLimit,
                               PaymentTerms paymentTerms, TaxTreatment taxTreatment, Long assignedRepId, String assignedRepName, Long sourceId, String sourceName,
                               String notes, String socialMedia, Long convertedFromLeadId, List<ContactResponse> contacts,
                               ShippingMethod preferredShipping, Instant archivedAt, long version) {
}
