package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ph.thecoffeejunkie.crm.entity.Quotation;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findTopByOrderByIdDesc();
//    Optional<Quotation> findByQuotationNumber(String number);
    Page<Quotation> findAll(Pageable pageable);
}