package ph.thecoffeejunkie.crm.followups;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

interface FollowUpRepository extends JpaRepository<FollowUp, Long>, JpaSpecificationExecutor<FollowUp> {

    List<FollowUp> findByCustomerIdAndAssigneeIdAndDoneAtIsNull(long customerId, long assigneeId);
}
