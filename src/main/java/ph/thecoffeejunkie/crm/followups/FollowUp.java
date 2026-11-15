package ph.thecoffeejunkie.crm.followups;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/** A dated to-do on a customer, optionally about one of its orders. Never deleted: mistakes are marked done. */
@Entity
@Table(name = "follow_up")
class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    long customerId;
    Long orderId;
    long assigneeId;
    String title;
    String note;
    LocalDate dueDate;
    Instant doneAt;
    Long doneBy;
    long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
