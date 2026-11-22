package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** A customer asks for a quote from the portal: products and quantities only; staff set the prices. */
record PortalQuoteRequest(@NotEmpty @Size(max = 50) @Valid List<Line> lines, @Size(max = 1000) String notes) {

    record Line(@NotNull Long productId, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty) {
    }
}
