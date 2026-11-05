package ph.thecoffeejunkie.crm.deals;

import ph.thecoffeejunkie.crm.sales.OrderBrief;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DealResponse(long id, long customerId, String customerName, Long repId, String repName, String title,
                           DealStage stage, BigDecimal value, LocalDate expectedClose, String lostReason,
                           String notes, Instant wonAt, Long wonOrderId, List<OrderBrief> orders, Instant createdAt,
                           Instant updatedAt, long version) {
}
