package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;

/** No cost, ever: minUnitPrice is the only floor the till sees. */
record PosProductResponse(long id, String sku, String name, ProductUnit unit, BigDecimal price, BigDecimal available,
                          BigDecimal minUnitPrice) {
}
