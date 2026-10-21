package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(long id);

    @Query(value = "select exists (select 1 from order_line where id = :lineId)", nativeQuery = true)
    boolean lineExists(long lineId);

    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextNumber();

    /** Σ balances of the customer's other confirmed-or-later, non-cancelled orders. */
    @Query(value = "select coalesce(sum(total - settled_amount), 0) from sales_order where customer_id = :customerId "
            + "and id <> :orderId and status in ('CONFIRMED','RELEASED','FULFILLED')", nativeQuery = true)
    BigDecimal openBalanceExcluding(long customerId, long orderId);

    @Query(value = "select exists (select 1 from sales_order where customer_id = :customerId "
            + "and status in ('CONFIRMED','RELEASED','FULFILLED') and due_date < :today "
            + "and settled_amount < total)", nativeQuery = true)
    boolean hasOverdue(long customerId, LocalDate today);
}
