package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long>,
        JpaSpecificationExecutor<StockAdjustment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAdjustment a where a.id = :id")
    Optional<StockAdjustment> findByIdForUpdate(long id);
}
