package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.customers.CustomerType;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;
import ph.thecoffeejunkie.crm.fulfilment.DeliverySummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * approvalVersion is the content fingerprint to put on approval requests. allowedActions is the only source of
 * truth for what the UI offers. deliveryOrder is the live (non-cancelled) delivery order, if any.
 */
record OrderResponse(long id, String number, long customerId, String customerName, CustomerType customerType,
                     Long repId, String repName, OrderStatus status, PaymentStatus paymentStatus, int revision,
                     PaymentTerms paymentTerms, LocalDate quoteValidUntil, boolean quoteExpired,
                     BigDecimal shippingFee, String notes, String terms, List<LineResponse> lines,
                     TotalsResponse totals, BigDecimal settled, BigDecimal balance, BigDecimal suggestedWithholding,
                     InvoiceSummary activeInvoice, Instant quotedAt, Instant customerAcceptedAt, Instant confirmedAt,
                     Instant releasedAt, Instant fulfilledAt, Instant cancelledAt, String cancelReason,
                     Set<OrderAction> allowedActions, long approvalVersion, Instant updatedAt, long version,
                     long shipFromWarehouseId, String shipFromWarehouseName, DeliveryMethod deliveryMethod,
                     BigDecimal totalShort, DeliverySummary deliveryOrder, SalesChannel channel) {
}
