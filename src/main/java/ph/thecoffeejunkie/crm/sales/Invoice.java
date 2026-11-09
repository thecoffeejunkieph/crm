package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Fully frozen at issue: never reads the live order or customer again. Never deleted; voided instead. */
@Entity
@Table(name = "invoice")
class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    Long orderId;
    int orderRevision;
    @Enumerated(EnumType.STRING)
    InvoiceStatus status = InvoiceStatus.ISSUED;
    Instant issuedAt;
    LocalDate dueDate;
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> customerSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> sellerSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    List<Map<String, Object>> linesSnapshot;
    BigDecimal vatableSales;
    BigDecimal vat;
    BigDecimal vatExemptSales;
    BigDecimal zeroRatedSales;
    BigDecimal shippingFee;
    BigDecimal total;
    String voidReason;
    Long voidedBy;
    Instant voidedAt;
    Long replacedById;
    Long issuedBy;

    InvoiceSummary toSummary() {
        return new InvoiceSummary(id, number, issuedAt, dueDate, total);
    }

    InvoiceResponse toResponse() {
        return new InvoiceResponse(id, number, status, orderRevision, issuedAt, dueDate, total, voidReason, voidedAt,
                replacedById, null);
    }
}
