package ph.thecoffeejunkie.crm.fulfilment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long>, JpaSpecificationExecutor<DeliveryOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeliveryOrder d where d.id = :id")
    Optional<DeliveryOrder> findByIdForUpdate(long id);

    /** At most one per order (partial unique index on non-cancelled rows): pass CANCELLED. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryOrder> findBySalesOrderIdAndStatusNot(long salesOrderId, DeliveryStatus status);

    Optional<DeliveryOrder> readBySalesOrderIdAndStatusNot(long salesOrderId, DeliveryStatus status);
}
