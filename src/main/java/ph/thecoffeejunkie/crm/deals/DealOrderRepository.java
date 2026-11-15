package ph.thecoffeejunkie.crm.deals;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DealOrderRepository extends JpaRepository<DealOrder, DealOrder.Key> {

    List<DealOrder> findByDealIdOrderByLinkedAtAscOrderIdAsc(long dealId);

    Optional<DealOrder> findByOrderId(long orderId);
}
