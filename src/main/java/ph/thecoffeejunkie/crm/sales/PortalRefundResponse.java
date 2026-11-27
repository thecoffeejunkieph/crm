package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A paid refund as the customer sees it. */
record PortalRefundResponse(long id, BigDecimal amount, LocalDate paidOn, String creditMemoNo) {
}
