package ph.thecoffeejunkie.crm.deals;

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
import java.time.LocalDate;

@Entity
@Table(name = "deal")
class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long customerId;
    Long repId;
    String title;
    @Enumerated(EnumType.STRING)
    DealStage stage = DealStage.DISCOVERY;
    BigDecimal value;
    LocalDate expectedClose;
    String lostReason;
    String notes;
    Instant wonAt;
    Long wonOrderId;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
