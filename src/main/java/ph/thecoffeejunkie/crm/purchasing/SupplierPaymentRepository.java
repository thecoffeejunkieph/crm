package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    List<SupplierPayment> findBySupplierIdOrderByPaidOnDescIdDesc(long supplierId);

    Optional<SupplierPayment> findByIdAndSupplierId(long id, long supplierId);

    @Query("select coalesce(sum(p.amount), 0) from SupplierPayment p where p.purchaseOrderId = :poId "
            + "and p.supplierId = (select o.supplierId from PurchaseOrder o where o.id = :poId)")
    BigDecimal sumByPurchaseOrderId(long poId);

    /** Rows of {poId, sum} for the POs that have payments; others are absent. */
    @Query("select p.purchaseOrderId, sum(p.amount) from SupplierPayment p join PurchaseOrder o on o.id = p.purchaseOrderId "
            + "where p.purchaseOrderId in :poIds and p.supplierId = o.supplierId group by p.purchaseOrderId")
    java.util.List<Object[]> sumsByPurchaseOrderIds(java.util.Collection<Long> poIds);
}
