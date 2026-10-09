package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ph.thecoffeejunkie.crm.entity.Quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findTopByOrderByIdDesc();
//    Optional<Quotation> findByQuotationNumber(String number);
    Page<Quotation> findAll(Pageable pageable);

    long countByStatusIn(List<String> statuses);

    List<Quotation> findByStatusInAndExpiryDateBefore(List<String> statuses, LocalDate date);

    long countByStatusAndQuoteDateBetween(String status, LocalDate start, LocalDate end);

    long countByStatusInAndQuoteDateBetween(List<String> statuses, LocalDate start, LocalDate end);

    @Query("select q.salesRep.email, q.salesRep.firstName, q.salesRep.lastName, " +
            "sum(q.totalAmount), count(q) " +
            "from Quotation q " +
            "where q.status = :status and q.salesRep is not null " +
            "and q.quoteDate between :start and :end " +
            "group by q.salesRep.email, q.salesRep.firstName, q.salesRep.lastName " +
            "order by sum(q.totalAmount) desc")
    List<Object[]> findTopSalesReps(@Param("status") String status,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    Pageable pageable);

    @Query("select q.customer.id, q.customer.firstName, q.customer.lastName, " +
            "sum(q.totalAmount), count(q) " +
            "from Quotation q " +
            "where q.status = :status and q.quoteDate between :start and :end " +
            "group by q.customer.id, q.customer.firstName, q.customer.lastName " +
            "order by sum(q.totalAmount) desc")
    List<Object[]> findTopCustomers(@Param("status") String status,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end,
                                    Pageable pageable);

    @Query("select i.product.id, i.product.productName, coalesce(sum(i.quantity), 0), coalesce(sum(i.total), 0) " +
            "from Quotation q join q.quotationItems i " +
            "where q.status = :status and q.quoteDate between :start and :end " +
            "group by i.product.id, i.product.productName " +
            "order by sum(i.total) desc")
    List<Object[]> findSalesByProduct(@Param("status") String status,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    @Query(value = "select date_format(q.quote_date, '%Y-%m') as ym, coalesce(sum(q.total_amount), 0) as total " +
            "from quotation q " +
            "where q.status = :status and q.quote_date between :start and :end " +
            "group by ym " +
            "order by ym", nativeQuery = true)
    List<Object[]> findMonthlySales(@Param("status") String status,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);
}
