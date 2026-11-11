package ph.thecoffeejunkie.crm.catalog;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record ProductRequest(@NotBlank @Size(max = 64) String sku, @NotBlank @Size(max = 200) String name, String description,
                      @NotNull ProductUnit unit,
                      @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal price,
                      @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal cost,
                      @NotNull TaxClass taxClass,
                      @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal reorderLevel, Long version) {
}
