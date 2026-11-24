package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findBySupplierIdOrderByPaidOnDescIdDesc(long supplierId);

    Optional<SupplierPayment> findByIdAndSupplierId(long id, long supplierId);
}
