package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderIdAndStatus(long orderId, InvoiceStatus status);

    List<Invoice> findByOrderIdOrderByIdDesc(long orderId);

    @Query("select i from Invoice i where i.orderId in (select o.id from SalesOrder o where o.customerId = :customerId)")
    Page<Invoice> findByCustomer(long customerId, Pageable page);
}
