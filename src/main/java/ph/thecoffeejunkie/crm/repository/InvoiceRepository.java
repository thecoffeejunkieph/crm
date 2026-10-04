package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ph.thecoffeejunkie.crm.entity.Invoice;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findTopByOrderByIdDesc();
    Page<Invoice> findAll(Pageable pageable);
    Page<Invoice> findByCustomerId(Long customerId, Pageable pageable);
    Optional<Invoice> findByQuotationId(Long quotationId);
}
