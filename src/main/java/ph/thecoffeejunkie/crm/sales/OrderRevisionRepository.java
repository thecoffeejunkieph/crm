package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface OrderRevisionRepository extends JpaRepository<OrderRevision, Long> {

    List<OrderRevision> findByOrderIdOrderByRevisionDesc(long orderId);
}
