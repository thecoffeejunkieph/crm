package ph.thecoffeejunkie.crm.insights;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface SalesCategoryTargetRepository extends JpaRepository<SalesCategoryTarget, Long> {

    List<SalesCategoryTarget> findByRepIdAndMonth(long repId, LocalDate month);

    Optional<SalesCategoryTarget> findByRepIdAndMonthAndCategoryId(long repId, LocalDate month, long categoryId);

    List<SalesCategoryTarget> findByMonth(LocalDate month);
}
