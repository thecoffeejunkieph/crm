package ph.thecoffeejunkie.crm.customers;

import java.math.BigDecimal;

/** What sales needs to price, check and invoice an order for a customer. */
public record CustomerSalesProfile(long id, CustomerType type, String displayName, String businessName, String tin,
                                   String address, BigDecimal creditLimit, PaymentTerms paymentTerms,
                                   TaxTreatment taxTreatment, Long assignedRepId, boolean archived,
                                   String email) {
}
