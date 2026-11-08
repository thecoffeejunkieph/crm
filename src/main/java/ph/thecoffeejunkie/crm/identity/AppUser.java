package ph.thecoffeejunkie.crm.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    /** Always stored trimmed and lower-cased. */
    String email;
    String passwordHash;
    String firstName;
    String lastName;
    String phone;
    /** Object key of the profile image in storage. */
    String avatarKey;
    @Enumerated(EnumType.STRING)
    Role role;
    String totpSecretEnc;
    Instant totpEnabledAt;
    Long totpLastStep;
    int failedLogins;
    Instant lockedUntil;
    String inviteEmailStatus;
    Instant archivedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_warehouse", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "warehouse_id")
    Set<Long> warehouseIds = new HashSet<>();

    String displayName() {
        return firstName + " " + lastName;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
