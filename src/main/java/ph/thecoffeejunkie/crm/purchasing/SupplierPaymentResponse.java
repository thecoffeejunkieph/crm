package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;

record SupplierPaymentResponse(long id, BigDecimal amount, LocalDate paidOn, String method, String reference, String notes,
                                   Long purchaseOrderId, String purchaseOrderNumber) {
}
