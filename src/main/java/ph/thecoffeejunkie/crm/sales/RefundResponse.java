package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RefundResponse(long id, long orderId, RefundKind kind, RefundStatus status, BigDecimal amount,
                             String reason, List<Line> lines, PaymentMethod method, String reference, String proofUrl,
                             LocalDate paidOn, String creditMemoNo, String requestedByName, Instant createdAt,
                             long version) {

    public record Line(long orderLineId, String description, long warehouseId, BigDecimal qty,
                       RefundDisposition disposition, BigDecimal amount) {
    }
}
