package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ph.thecoffeejunkie.crm.customers.PaymentTerms;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The one sales aggregate, from draft to fulfilled (spec §2). Lines keep their ids across edits. */
@Entity
@Table(name = "sales_order")
class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    Long customerId;
    Long repId;
    @Enumerated(EnumType.STRING)
    OrderStatus status = OrderStatus.DRAFT;
    int revision;
    @Enumerated(EnumType.STRING)
    PaymentTerms paymentTerms;
    LocalDate quoteValidUntil;
    BigDecimal shippingFee = new BigDecimal("0.00");
    String notes;
    String terms;
    BigDecimal vatableSales = new BigDecimal("0.00");
    BigDecimal vat = new BigDecimal("0.00");
    BigDecimal vatExemptSales = new BigDecimal("0.00");
    BigDecimal zeroRatedSales = new BigDecimal("0.00");
    BigDecimal total = new BigDecimal("0.00");
    BigDecimal settledAmount = new BigDecimal("0.00");
    LocalDate dueDate;
    Instant quotedAt;
    Instant customerAcceptedAt;
    Instant confirmedAt;
    Instant releasedAt;
    Instant fulfilledAt;
    Instant cancelledAt;
    String cancelReason;
    Long shipFromWarehouseId;
    @Enumerated(EnumType.STRING)
    DeliveryMethod deliveryMethod = DeliveryMethod.DELIVERY;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    List<OrderLine> lines = new ArrayList<>();

    OrderTotals.Result totals() {
        return new OrderTotals.Result(lines.stream().map(l -> l.lineTotal).toList(), vatableSales, vat,
                vatExemptSales, zeroRatedSales, total);
    }

    BigDecimal balance() {
        return total.subtract(settledAmount);
    }
}
