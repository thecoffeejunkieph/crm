package ph.thecoffeejunkie.crm.leads;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "lead")
class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String contactName;
    String company;
    String phone;
    String email;
    String socialMedia;
    String address;
    String interest;
    String notes;
    Long sourceId;
    Long assignedRepId;
    @Enumerated(EnumType.STRING)
    LeadStatus status = LeadStatus.NEW;
    String lostReason;
    Long customerId;
    Instant convertedAt;
    Long convertedBy;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
