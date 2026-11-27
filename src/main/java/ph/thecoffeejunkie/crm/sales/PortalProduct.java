package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.catalog.ProductInfo;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;

/** A product as the portal shows it: never price or cost. qty is set only on reorder lines. */
record PortalProduct(long id, String sku, String name, ProductUnit unit, BigDecimal qty) {

    static PortalProduct of(ProductInfo p, BigDecimal qty) {
        return new PortalProduct(p.id(), p.sku(), p.name(), p.unit(), qty);
    }
}
