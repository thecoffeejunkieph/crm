package ph.thecoffeejunkie.crm.customers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface LeadSourceRepository extends JpaRepository<LeadSource, Long> {

    Optional<LeadSource> findByNameIgnoreCase(String name);

    List<LeadSource> findAllByOrderByNameAsc();

    List<LeadSource> findByArchivedAtIsNullOrderByNameAsc();
}
