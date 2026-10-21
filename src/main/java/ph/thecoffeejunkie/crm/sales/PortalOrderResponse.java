package ph.thecoffeejunkie.crm.sales;

import ph.thecoffeejunkie.crm.customers.PaymentTerms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What a customer contact sees: no notes, rep, cost, approval details or proof of delivery. */
record PortalOrderResponse(long id, String number, OrderStatus status, PaymentStatus paymentStatus,
                           LocalDate quoteValidUntil, boolean quoteExpired, boolean canAccept, PaymentTerms paymentTerms,
                           BigDecimal shippingFee, String terms, List<PortalLine> lines, TotalsResponse totals,
                           BigDecimal settled, BigDecimal balance, InvoiceSummary activeInvoice,
                           Instant customerAcceptedAt, long version, PortalDeliveryStatus deliveryStatus,
                           Instant deliveredAt) {
}
