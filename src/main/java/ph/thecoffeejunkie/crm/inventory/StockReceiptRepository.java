package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface StockReceiptRepository extends JpaRepository<StockReceipt, Long>, JpaSpecificationExecutor<StockReceipt> {
}
