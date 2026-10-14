package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(long id);

    @Query(value = "select nextval('sales_order_number_seq')", nativeQuery = true)
    long nextNumber();
}
