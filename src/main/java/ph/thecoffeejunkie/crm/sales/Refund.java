package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ph.thecoffeejunkie.crm.catalog.TaxClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Money going back to the customer for an order; GOODS_RETURN also carries the lines being returned. */
@Entity
@Table(name = "refund")
class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long orderId;
    @Enumerated(EnumType.STRING)
    RefundKind kind;
    BigDecimal amount;
    String reason;
    @Enumerated(EnumType.STRING)
    RefundStatus status;
    Long approvalRequestId;
    @Enumerated(EnumType.STRING)
    PaymentMethod method;
    String reference;
    String proofKey;
    LocalDate paidOn;
    Long paidBy;
    Instant paidAt;
    String creditMemoNo;
    /** The order's ISSUED invoice at payout; the memo cites it even after a reissue. */
    Long invoiceId;
    /** MONEY_ONLY: the tax class the memo uses, frozen at payout. GOODS_RETURN: null (each line's class). */
    @Enumerated(EnumType.STRING)
    TaxClass memoTaxClass;
    Long requestedBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "refund_id", nullable = false)
    @OrderBy("id")
    List<RefundLine> lines = new ArrayList<>();
}
