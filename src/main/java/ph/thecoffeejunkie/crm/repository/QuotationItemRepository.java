package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.QuotationItem;

@Repository
public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
}
