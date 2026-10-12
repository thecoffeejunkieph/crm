package ph.thecoffeejunkie.crm.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailAndArchivedAtIsNull(String email);

    // Login mirrors LoginThrottle into the row with single atomic UPDATEs: no read-modify-write, no version bump.

    @Modifying
    @Query("update AppUser u set u.failedLogins = u.failedLogins + 1 where u.id = :id")
    void incrementFailedLogins(long id);

    @Modifying
    @Query("update AppUser u set u.failedLogins = 0, u.lockedUntil = :until where u.id = :id")
    void lockLogin(long id, Instant until);

    @Modifying
    @Query("update AppUser u set u.failedLogins = 0, u.lockedUntil = null where u.id = :id")
    void resetFailedLogins(long id);
}
