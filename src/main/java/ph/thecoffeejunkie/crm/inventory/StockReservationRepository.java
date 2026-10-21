package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findBySalesOrderId(long salesOrderId);

    /** (productId, warehouseId) pairs, read without loading entities so a later read after locking is fresh. */
    @Query("select distinct r.productId, r.warehouseId from StockReservation r where r.salesOrderId = :salesOrderId")
    List<Object[]> levelKeys(long salesOrderId);

    Optional<StockReservation> findByOrderLineId(long orderLineId);

    /** The line's (productId, warehouseId), read without loading the entity, so the read after locking is fresh. */
    @Query("select r.productId, r.warehouseId from StockReservation r where r.orderLineId = :orderLineId")
    List<Object[]> lineKey(long orderLineId);

    /** Allocation order: the linked order first, then oldest confirmed, then order, then line. */
    @Query("select r from StockReservation r where r.productId = :productId and r.warehouseId = :warehouseId "
            + "and r.shortQty > 0 order by case when r.salesOrderId = :linkedOrderId then 0 else 1 end, "
            + "r.confirmedAt, r.salesOrderId, r.orderLineId")
    List<StockReservation> findShort(long productId, long warehouseId, Long linkedOrderId);

    /** Take-back order: youngest confirmed first. */
    @Query("select r from StockReservation r where r.productId = :productId and r.warehouseId = :warehouseId "
            + "and r.reservedQty > 0 order by r.confirmedAt desc, r.salesOrderId desc, r.orderLineId desc")
    List<StockReservation> findReservedYoungestFirst(long productId, long warehouseId);

    @Query("select coalesce(sum(r.shortQty), 0) from StockReservation r where r.salesOrderId = :salesOrderId")
    BigDecimal totalShort(long salesOrderId);
}
