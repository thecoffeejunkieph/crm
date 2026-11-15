package ph.thecoffeejunkie.crm.inventory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_receipt")
class StockReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String number;
    long warehouseId;
    String supplierName;
    String reference;
    LocalDate receivedOn;
    String notes;
    Long createdBy;
    Instant createdAt = Instant.now();
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL)
    @OrderBy("id")
    List<StockReceiptLine> lines = new ArrayList<>();
}
