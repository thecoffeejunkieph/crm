package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @Modifying
    @Query(value = "insert into stock_level (product_id, warehouse_id) values (:productId, :warehouseId) "
            + "on conflict do nothing", nativeQuery = true)
    void insertIfMissing(long productId, long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockLevel> findByProductIdAndWarehouseId(long productId, long warehouseId);
}
