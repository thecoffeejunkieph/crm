package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findBySalesOrderId(long salesOrderId);

    /** Allocation order: the linked order first, then oldest confirmed, then order, then line. */
    @Query("select r from StockReservation r where r.productId = :productId and r.warehouseId = :warehouseId "
            + "and r.shortQty > 0 order by case when r.salesOrderId = :linkedOrderId then 0 else 1 end, "
            + "r.confirmedAt, r.salesOrderId, r.orderLineId")
    List<StockReservation> findShort(long productId, long warehouseId, Long linkedOrderId);

    @Query("select coalesce(sum(r.shortQty), 0) from StockReservation r where r.salesOrderId = :salesOrderId")
    BigDecimal totalShort(long salesOrderId);
}
