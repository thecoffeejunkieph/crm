package ph.thecoffeejunkie.crm.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderIdAndStatus(long orderId, InvoiceStatus status);

    List<Invoice> findByOrderIdOrderByIdDesc(long orderId);
}
