package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByOrderIdAndStatus(long orderId, InvoiceStatus status);

    List<Invoice> findByOrderIdOrderByIdDesc(long orderId);
}
