package ph.thecoffeejunkie.crm.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Matches the partial unique index product_sku_uq. */
    @Query("select lower(p.sku) from Product p where p.archivedAt is null")
    Set<String> activeSkus();
}
