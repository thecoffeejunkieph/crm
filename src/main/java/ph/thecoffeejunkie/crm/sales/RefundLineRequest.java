package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RefundLineRequest(long orderLineId, long warehouseId, @NotNull @Positive BigDecimal qty,
                                @NotNull RefundDisposition disposition) {
}
