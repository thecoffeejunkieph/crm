package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "refund_line")
class RefundLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    Long orderLineId;
    Long warehouseId;
    BigDecimal qty;
    @Enumerated(EnumType.STRING)
    RefundDisposition disposition;
    BigDecimal amount;
}
