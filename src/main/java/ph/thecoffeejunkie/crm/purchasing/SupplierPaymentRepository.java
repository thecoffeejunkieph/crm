package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findBySupplierIdOrderByPaidOnDescIdDesc(long supplierId);

    Optional<SupplierPayment> findByIdAndSupplierId(long id, long supplierId);

    @Query("select coalesce(sum(p.amount), 0) from SupplierPayment p where p.purchaseOrderId = :poId")
    BigDecimal sumByPurchaseOrderId(long poId);
}
