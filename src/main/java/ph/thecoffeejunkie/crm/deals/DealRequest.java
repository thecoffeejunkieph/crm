package ph.thecoffeejunkie.crm.deals;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code customerId} is required on create and ignored on edit; {@code version} is for edit. */
public record DealRequest(Long customerId, @NotBlank @Size(max = 160) String title,
                          @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal value, LocalDate expectedClose,
                          @Size(max = 4000) String notes, Long version) {

    public DealRequest {
        title = title == null ? null : title.trim();
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}
