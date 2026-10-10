package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndProductNameContainingIgnoreCase(String productName, Pageable pageable);

    // POS grid: null q / categoryId mean "no filter". Left join keeps uncategorized products.
    @Query("select p from Product p left join p.category c where p.active = true"
            + " and (:q is null or lower(p.productName) like lower(concat('%', :q, '%')))"
            + " and (:categoryId is null or c.id = :categoryId)")
    Page<Product> searchActive(@Param("q") String q, @Param("categoryId") Long categoryId, Pageable pageable);
}
