package ph.thecoffeejunkie.crm.purchasing;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_line")
class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne
    @JoinColumn(name = "po_id")
    PurchaseOrder po;
    long productId;
    BigDecimal qty;
    BigDecimal unitCost;
    BigDecimal receivedQty = BigDecimal.ZERO;
}
