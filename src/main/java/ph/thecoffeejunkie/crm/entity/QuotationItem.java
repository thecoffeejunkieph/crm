package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.DiscountType;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuotationItem extends BaseEntity {

    private Integer quantity;
    private BigDecimal price;
    private Integer discount;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private BigDecimal total;

    @ManyToOne (fetch = FetchType.LAZY)
    @JsonBackReference
    private Quotation quotation;

    @ManyToOne (fetch = FetchType.LAZY)
    @JsonBackReference
    private Product product;
}
