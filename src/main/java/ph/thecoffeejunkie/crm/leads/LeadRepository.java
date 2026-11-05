package ph.thecoffeejunkie.crm.leads;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    /** [status, count] rows; a null rep counts everyone's. */
    @Query("select l.status, count(l) from Lead l where (:repId is null or l.assignedRepId = :repId) group by l.status")
    List<Object[]> countByStatus(Long repId);
}
