package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByIdDesc(long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findByIdForUpdate(long id);

    /** Money already claimed by refunds that are asked for or approved but not yet paid. */
    @Query(value = "select coalesce(sum(amount), 0) from refund where order_id = :orderId "
            + "and status in ('REQUESTED','APPROVED')", nativeQuery = true)
    BigDecimal openAmount(long orderId);
}
