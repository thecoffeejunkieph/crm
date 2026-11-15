package ph.thecoffeejunkie.crm.inventory;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfer")
class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    long fromWarehouseId;
    long toWarehouseId;
    @Enumerated(EnumType.STRING)
    TransferStatus status = TransferStatus.DRAFT;
    Long salesOrderId;
    String notes;
    Instant dispatchedAt;
    Long dispatchedBy;
    Instant receivedAt;
    Long receivedBy;
    Long createdBy;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    List<StockTransferLine> lines = new ArrayList<>();
}
