package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    Page<StockReceipt> findByWarehouseId(long warehouseId, Pageable pageable);
}
