package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
