package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.InventoryItem;

import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    Page<InventoryItem> findByProductId(Long productId, Pageable pageable);
    Page<InventoryItem> findByWarehouseId(Long warehouseId, Pageable pageable);
    Page<InventoryItem> findByProductIdAndWarehouseId(Long productId, Long warehouseId, Pageable pageable);
}
