package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;

public record TotalsResponse(BigDecimal vatableSales, BigDecimal vat, BigDecimal vatExemptSales,
                             BigDecimal zeroRatedSales, BigDecimal total) {

    static TotalsResponse of(OrderTotals.Result r) {
        return new TotalsResponse(r.vatableSales(), r.vat(), r.vatExemptSales(), r.zeroRatedSales(), r.total());
    }
}
