package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {
    Page<DeliveryOrder> findByStatus(DeliveryOrderStatus status, Pageable pageable);
    Page<DeliveryOrder> findByStatusIn(List<DeliveryOrderStatus> statuses, Pageable pageable);
    long countByStatusIn(List<DeliveryOrderStatus> statuses);
    long countByStatusAndPickedUpAtBetween(DeliveryOrderStatus status, LocalDateTime start, LocalDateTime end);
    long countByStatusAndDeliveredAtBetween(DeliveryOrderStatus status, LocalDateTime start, LocalDateTime end);
    Optional<DeliveryOrder> findTopByOrderByIdDesc();
}
