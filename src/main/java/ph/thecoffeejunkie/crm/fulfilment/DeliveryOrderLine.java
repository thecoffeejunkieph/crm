package ph.thecoffeejunkie.crm.fulfilment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_order_line")
class DeliveryOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_order_id")
    DeliveryOrder deliveryOrder;
    long orderLineId;
    long productId;
    String description;
    @Enumerated(EnumType.STRING)
    ProductUnit unit;
    BigDecimal qty;
    /** Null until picked. */
    BigDecimal pickedQty;
}
