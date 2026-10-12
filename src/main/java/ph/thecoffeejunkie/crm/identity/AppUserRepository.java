package ph.thecoffeejunkie.crm.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailAndArchivedAtIsNull(String email);

    /** SELECT ... FOR UPDATE: concurrent logins for one user serialize, so every failure is counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.email = :email and u.archivedAt is null")
    Optional<AppUser> lockForLogin(String email);
}
