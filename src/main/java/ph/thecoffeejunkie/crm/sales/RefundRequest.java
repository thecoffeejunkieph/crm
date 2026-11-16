package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** amount is for MONEY_ONLY, lines for GOODS_RETURN (whose amount is computed from the lines). */
public record RefundRequest(@NotNull RefundKind kind, BigDecimal amount, @NotBlank String reason,
                            @Valid List<RefundLineRequest> lines) {
}
