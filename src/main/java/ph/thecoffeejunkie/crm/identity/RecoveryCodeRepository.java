package ph.thecoffeejunkie.crm.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    /** Atomic single-use: of two parallel attempts with the same code only one updates a row. */
    @Modifying
    @Query("update RecoveryCode c set c.usedAt = :now where c.userId = :userId and c.codeHash = :hash and c.usedAt is null")
    int markUsed(long userId, String hash, Instant now);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteAllFor(long userId);
}
