package ph.thecoffeejunkie.crm.purchasing;

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
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "purchase_order")
class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    long supplierId;
    long warehouseId;
    @Enumerated(EnumType.STRING)
    PoStatus status = PoStatus.DRAFT;
    LocalDate expectedOn;
    String notes;
    String rejectionReason;
    Long approvalRequestId;
    Instant sentAt;
    boolean closedShort;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
    @OneToMany(mappedBy = "po", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    List<PurchaseOrderLine> lines = new ArrayList<>();

    void requireStatus(PoStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new DomainException(ErrorCode.CONFLICT,
                    "Purchase order " + number + " is " + status + "; this needs " + Arrays.toString(allowed) + ".");
        }
    }

    /** Header and lines are editable in DRAFT only. */
    void requireEditable() {
        requireStatus(PoStatus.DRAFT);
    }

    /** From DRAFT, PENDING_APPROVAL or SENT, and only while nothing has been received. */
    void cancel() {
        requireStatus(PoStatus.DRAFT, PoStatus.PENDING_APPROVAL, PoStatus.SENT);
        if (lines.stream().anyMatch(l -> l.receivedQty.signum() > 0)) {
            throw new DomainException(ErrorCode.CONFLICT,
                    "Purchase order " + number + " has received stock and cannot be cancelled.");
        }
        status = PoStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    /** Each line rounded HALF_UP to 2 decimals, then summed. */
    BigDecimal total() {
        return lines.stream().map(l -> l.qty.multiply(l.unitCost).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
