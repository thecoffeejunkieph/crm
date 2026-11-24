package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record PoResponse(long id, String number, long supplierId, String supplierName, long warehouseId,
                         PoStatus status, LocalDate expectedOn, String notes, String rejectionReason,
                         Long approvalRequestId, Instant sentAt, boolean closedShort, BigDecimal total,
                         List<LineResponse> lines, long version) {

    record LineResponse(long id, long productId, String sku, String name, BigDecimal qty, BigDecimal unitCost,
                               BigDecimal receivedQty) {
    }
}
