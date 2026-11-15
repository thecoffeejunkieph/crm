package ph.thecoffeejunkie.crm.sales;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.catalog.TaxClass;

import java.math.BigDecimal;

/** belowCost is set for ADMIN only (it reveals cost). reservedQty/shortQty are null until the order is confirmed. */
record LineResponse(long id, int position, long productId, String sku, String description, ProductUnit unit,
                    BigDecimal qty, BigDecimal unitPrice, BigDecimal discountPct, TaxClass taxClass,
                    BigDecimal lineTotal, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean belowCost,
                    BigDecimal reservedQty, BigDecimal shortQty) {
}
