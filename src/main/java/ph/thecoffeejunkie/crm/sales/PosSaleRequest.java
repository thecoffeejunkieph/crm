package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** One counter sale. tendered is the cash handed over (CASH only, at least amount); change is the difference. */
record PosSaleRequest(@NotNull Long customerId, @NotEmpty @Size(max = 200) List<@Valid Line> lines,
                      @NotEmpty @Size(max = 10) List<@Valid Tender> payments, boolean emailInvoice) {

    record Line(@NotNull Long productId, @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal qty,
                @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
                @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPct) {
    }

    record Tender(@NotNull PaymentMethod method, @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
                  @Positive @Digits(integer = 17, fraction = 2) BigDecimal tendered, @Size(max = 100) String reference) {
    }
}
