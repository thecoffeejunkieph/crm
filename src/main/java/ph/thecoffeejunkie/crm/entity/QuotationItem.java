package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private BigDecimal total;

    @ManyToOne
    @JsonBackReference
    private Quotation quotation;

    @ManyToOne
    @JsonBackReference
    private Product product;
}
