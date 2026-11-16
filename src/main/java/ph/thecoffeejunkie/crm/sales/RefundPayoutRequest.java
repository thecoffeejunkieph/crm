package ph.thecoffeejunkie.crm.sales;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RefundPayoutRequest(@NotNull PaymentMethod method, String reference, @NotNull LocalDate paidOn,
                                  long version) {
}
