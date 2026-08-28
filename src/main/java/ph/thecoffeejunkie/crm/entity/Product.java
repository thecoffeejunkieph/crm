package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import ph.thecoffeejunkie.crm.constant.Unit;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Product extends BaseEntity {

    @NotBlank
    private String productName;

    private String description;

    private Unit unit;

    @NotNull
    @Positive
    private BigDecimal price;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    private String picturePath;

    @ManyToOne
    @JsonBackReference
    private Quotation quotation;

    @OneToMany(mappedBy = "product")
    private List<QuotationItem> quotationItem;
}
