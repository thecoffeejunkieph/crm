package ph.thecoffeejunkie.crm.deals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    /** [stage, count, sum of values] rows; a null rep counts everyone's. */
    @Query("select d.stage, count(d), coalesce(sum(d.value), 0) from Deal d "
            + "where (:repId is null or d.repId = :repId) group by d.stage")
    List<Object[]> totalsByStage(Long repId);

    List<Deal> findByCustomerIdAndStageIn(long customerId, java.util.Collection<DealStage> stages);
}
