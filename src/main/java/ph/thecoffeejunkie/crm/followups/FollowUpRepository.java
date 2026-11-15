package ph.thecoffeejunkie.crm.followups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import java.util.List;

interface FollowUpRepository extends JpaRepository<FollowUp, Long>, JpaSpecificationExecutor<FollowUp> {

    List<FollowUp> findByCustomerIdAndAssigneeIdAndDoneAtIsNull(long customerId, long assigneeId);

    List<FollowUp> findByCustomerIdAndDoneAtIsNull(long customerId);

    /** Conditional, so a concurrent second tick re-checks after the row lock and changes nothing (no 409). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FollowUp f set f.doneAt = :at, f.doneBy = :by, f.updatedAt = :at, f.version = f.version + 1 "
            + "where f.id = :id and f.doneAt is null")
    int markDone(long id, Instant at, long by);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FollowUp f set f.doneAt = null, f.doneBy = null, f.updatedAt = :at, f.version = f.version + 1 "
            + "where f.id = :id and f.doneAt is not null")
    int markOpen(long id, Instant at);
}
