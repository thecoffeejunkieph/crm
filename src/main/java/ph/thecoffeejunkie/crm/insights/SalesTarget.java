package ph.thecoffeejunkie.crm.insights;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A rep's target and commission rate for one month; {@code month} is the first day of it. */
@Entity
@Table(name = "sales_target")
class SalesTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long repId;
    LocalDate month;
    BigDecimal targetAmount;
    BigDecimal commissionPct;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    long updatedBy;
    @Version
    long version;
}
