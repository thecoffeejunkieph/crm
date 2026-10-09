package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.thecoffeejunkie.crm.entity.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findTopByOrderByIdDesc();
    Page<Invoice> findAll(Pageable pageable);
    Page<Invoice> findByCustomerId(Long customerId, Pageable pageable);
    Optional<Invoice> findByQuotationId(Long quotationId);

    // ponytail: profit uses the product's current cost (null cost = 0), not a snapshot at sale time.
    @Query("select inv.invoiceDate, coalesce(sum(i.total), 0), " +
            "coalesce(sum(i.total - i.quantity * coalesce(i.product.cost, 0)), 0) " +
            "from Invoice inv join inv.invoiceItems i " +
            "where inv.status = ph.thecoffeejunkie.crm.constant.InvoiceStatus.PAID " +
            "and inv.invoiceDate between :start and :end " +
            "group by inv.invoiceDate")
    List<Object[]> findDailyPaidSalesAndProfit(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select inv.invoiceDate, count(inv) from Invoice inv " +
            "where inv.status <> ph.thecoffeejunkie.crm.constant.InvoiceStatus.CANCELLED " +
            "and inv.invoiceDate between :start and :end " +
            "group by inv.invoiceDate")
    List<Object[]> countDailyInvoices(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
