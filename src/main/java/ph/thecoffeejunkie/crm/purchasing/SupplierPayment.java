package ph.thecoffeejunkie.crm.purchasing;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "supplier_payment")
class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long supplierId;
    BigDecimal amount;
    LocalDate paidOn;
    String method;
    String reference;
    String notes;
    Long createdBy;
    Instant createdAt = Instant.now();
}
