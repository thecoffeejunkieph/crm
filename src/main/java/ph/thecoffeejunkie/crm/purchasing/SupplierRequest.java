package ph.thecoffeejunkie.crm.purchasing;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** {@code creditLimit} null means no limit. {@code version} is required on update. */
record SupplierRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String name,
                       String contactName, String phone, String email, String address,
                       @PositiveOrZero Integer paymentTermsDays, @PositiveOrZero Integer leadTimeDays,
                       @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
                       @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal openingBalance,
                       String notes, Long version) {
}
