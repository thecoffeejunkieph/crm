package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
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

    /** Cash actually received on VERIFIED payments: withheld tax went to the BIR, so it is never refundable. */
    @Query(value = "select coalesce(sum(cash_amount), 0) from payment where order_id = :orderId "
            + "and status = 'VERIFIED'", nativeQuery = true)
    BigDecimal cashCollected(long orderId);

    /** {@link #cashCollected(long)} for a page of orders in one query: rows of (order_id, cash); none when unpaid. */
    @Query(value = "select order_id, sum(cash_amount) from payment where order_id in (:orderIds) "
            + "and status = 'VERIFIED' group by order_id", nativeQuery = true)
    List<Object[]> cashCollected(Collection<Long> orderIds);

    /** Per order line: qty and amount claimed by refunds that are not rejected or cancelled. */
    @Query(value = "select rl.order_line_id, sum(rl.qty), sum(rl.amount) from refund_line rl "
            + "join refund r on r.id = rl.refund_id where r.order_id = :orderId "
            + "and r.status not in ('REJECTED','CANCELLED') group by rl.order_line_id", nativeQuery = true)
    List<Object[]> claimedByLine(long orderId);
}
