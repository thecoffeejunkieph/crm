package ph.thecoffeejunkie.crm.insights;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    Optional<SalesTarget> findByRepIdAndMonth(long repId, LocalDate month);

    List<SalesTarget> findByMonth(LocalDate month);
}
