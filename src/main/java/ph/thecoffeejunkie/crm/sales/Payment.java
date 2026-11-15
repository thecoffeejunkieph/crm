package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.Column;
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

/** Attached to the order, not the invoice, so void/reissue needs no carry-over. Only VERIFIED ones settle. */
@Entity
@Table(name = "payment")
class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long orderId;
    @Enumerated(EnumType.STRING)
    PaymentVerification status;
    @Enumerated(EnumType.STRING)
    PaymentMethod method;
    BigDecimal cashAmount;
    BigDecimal withholdingAmount;
    LocalDate receivedOn;
    String reference;
    String proofKey;
    @Column(name = "form2307_key")
    String form2307Key;
    String submittedByType;
    Long submittedById;
    Long verifiedBy;
    Instant verifiedAt;
    String rejectReason;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;

    BigDecimal amount() {
        return cashAmount.add(withholdingAmount);
    }
}
