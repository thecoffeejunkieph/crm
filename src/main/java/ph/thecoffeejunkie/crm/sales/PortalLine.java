package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;

record PortalLine(String description, ProductUnit unit, BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct,
                  BigDecimal lineTotal) {
}
