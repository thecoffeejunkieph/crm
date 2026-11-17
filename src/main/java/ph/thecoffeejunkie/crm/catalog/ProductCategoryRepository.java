package ph.thecoffeejunkie.crm.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    List<ProductCategory> findAllByOrderByNameAsc();

    List<ProductCategory> findByArchivedAtIsNullOrderByNameAsc();
}
