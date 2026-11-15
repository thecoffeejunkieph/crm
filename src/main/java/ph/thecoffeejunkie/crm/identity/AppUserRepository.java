package ph.thecoffeejunkie.crm.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmailAndArchivedAtIsNull(String email);

    /**
     * Row-locks the active admins so concurrent demotions/archivals cannot both pass the last-admin guard.
     * Ordered by id so two transactions lock in the same order and cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findByRoleAndArchivedAtIsNullOrderById(Role role);

    @Query("select u.id from AppUser u where u.role = :role and u.archivedAt is null order by u.id")
    List<Long> activeIdsWithRole(Role role);

    @Query("select u.id from AppUser u join u.warehouseIds w where w = :warehouseId and u.role = :role "
            + "and u.archivedAt is null order by u.id")
    List<Long> activeIdsForWarehouse(Role role, long warehouseId);

    boolean existsByIdAndRoleAndArchivedAtIsNull(long id, Role role);

    boolean existsByRoleAndArchivedAtIsNullAndPasswordHashIsNotNull(Role role);

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

    @Modifying
    @Query("update AppUser u set u.inviteEmailStatus = :status where u.id = :id")
    void setInviteEmailStatus(long id, String status);

    // TOTP writes are single UPDATEs too, so they never fight login's counters over the row version.

    /** Replay guard: succeeds only if this step is newer than any already used. */
    @Modifying
    @Query("update AppUser u set u.totpLastStep = :step where u.id = :id and (u.totpLastStep is null or u.totpLastStep < :step)")
    int advanceTotpStep(long id, long step);

    @Modifying
    @Query("update AppUser u set u.totpSecretEnc = :enc, u.totpEnabledAt = :now, u.totpLastStep = :step "
            + "where u.id = :id and u.totpEnabledAt is null")
    int enableTotp(long id, String enc, Instant now, long step);

    @Modifying
    @Query("update AppUser u set u.totpSecretEnc = null, u.totpEnabledAt = null, u.totpLastStep = null where u.id = :id")
    void clearTotp(long id);
}
