package ph.thecoffeejunkie.crm.catalog;

import java.math.BigDecimal;

/** For sales pricing and checks. Carries cost: never put it in a non-ADMIN response. */
public record ProductInfo(long id, String sku, String name, ProductUnit unit, BigDecimal price, BigDecimal cost,
                          TaxClass taxClass, boolean archived) {
}
