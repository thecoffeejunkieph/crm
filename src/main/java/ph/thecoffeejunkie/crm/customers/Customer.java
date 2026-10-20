package ph.thecoffeejunkie.crm.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "customer")
class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Enumerated(EnumType.STRING)
    CustomerType type;
    String displayName;
    String businessName;
    String tin;
    String address;
    String phone;
    String email;
    String socialMedia;
    BigDecimal creditLimit;
    @Enumerated(EnumType.STRING)
    PaymentTerms paymentTerms;
    @Enumerated(EnumType.STRING)
    TaxTreatment taxTreatment = TaxTreatment.REGULAR;
    Long assignedRepId;
    String source;
    String notes;
    Instant archivedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
