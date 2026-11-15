package ph.thecoffeejunkie.crm.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recovery_code")
class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long userId;
    /** SHA-256 hex of the code; the code itself is shown once and never stored. */
    String codeHash;
    Instant usedAt;
}
