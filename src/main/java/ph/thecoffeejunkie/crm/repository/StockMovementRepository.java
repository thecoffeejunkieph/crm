package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.StockMovement;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);
}
