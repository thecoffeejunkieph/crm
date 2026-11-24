package ph.thecoffeejunkie.crm.purchasing;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "supplier")
class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String code;
    String name;
    String contactName;
    String phone;
    String email;
    String address;
    Integer paymentTermsDays;
    Integer leadTimeDays;
    BigDecimal creditLimit;
    BigDecimal openingBalance = BigDecimal.ZERO;
    String notes;
    Instant archivedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
