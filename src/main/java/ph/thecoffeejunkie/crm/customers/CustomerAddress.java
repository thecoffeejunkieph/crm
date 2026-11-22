package ph.thecoffeejunkie.crm.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "customer_address")
class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long customerId;
    String label;
    String address;
    @Column(name = "is_default")
    boolean isDefault;
    Instant archivedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}