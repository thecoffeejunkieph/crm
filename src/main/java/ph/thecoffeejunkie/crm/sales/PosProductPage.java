package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.math.BigDecimal;

record PosProductPage(PageResponse<PosProductResponse> products, BigDecimal discountCapPct) {
}
