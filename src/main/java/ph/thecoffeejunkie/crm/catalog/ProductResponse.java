package ph.thecoffeejunkie.crm.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code cost} is filled for ADMIN only and omitted from the JSON otherwise. */
record ProductResponse(long id, String sku, String name, String description, ProductUnit unit, BigDecimal price,
                       @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal cost, TaxClass taxClass,
                       BigDecimal reorderLevel, Instant archivedAt, long version) {
}
