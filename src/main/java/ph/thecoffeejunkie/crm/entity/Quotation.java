package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Quotation extends BaseEntity {

    private String quotationNumber;

    @ManyToOne
    @JsonBackReference
    private Customer customer;

    @OneToMany
    @JoinTable(
            name = "quotation_item_details",
            joinColumns = @JoinColumn(name = "quotation_id"),
            inverseJoinColumns = @JoinColumn(name = "quotation_item_id")
    )
    @JsonManagedReference
    private List<QuotationItem> quotationItems;

    private String status;
    private BigDecimal totalAmount;

    private LocalDateTime quoteDate;
    private LocalDateTime expiryDate;
    private BigDecimal shippingCharges;
    private String notes;
    private String termsAndConditions;
}
