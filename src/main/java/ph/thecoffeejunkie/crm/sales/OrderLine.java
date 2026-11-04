package ph.thecoffeejunkie.crm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.catalog.TaxClass;

import java.math.BigDecimal;

/** description, unit and taxClass are snapshots: catalog edits don't rewrite orders. */
@Entity
@Table(name = "order_line")
class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne
    @JoinColumn(name = "order_id")
    SalesOrder order;
    int position;
    Long productId;
    String description;
    @Enumerated(EnumType.STRING)
    ProductUnit unit;
    BigDecimal qty;
    BigDecimal unitPrice;
    BigDecimal discountPct;
    @Enumerated(EnumType.STRING)
    TaxClass taxClass;
    BigDecimal lineTotal;
    /** Product cost when the line was confirmed (spec 2026-11-05); admin-only, never in a response. */
    @Column(name = "unit_cost")
    BigDecimal unitCost;

    /** unitPrice after discount, unrounded: what the below-cost check compares with cost. */
    BigDecimal netUnitPrice() {
        return unitPrice.multiply(new BigDecimal(100).subtract(discountPct)).divide(new BigDecimal(100));
    }

    OrderTotals.Line toTotalsLine() {
        return new OrderTotals.Line(qty, unitPrice, discountPct, taxClass);
    }
}
