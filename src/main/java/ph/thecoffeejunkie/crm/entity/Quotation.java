package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_email")
    private CRMUser salesRep;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "quotation_item_details",
            joinColumns = @JoinColumn(name = "quotation_id"),
            inverseJoinColumns = @JoinColumn(name = "quotation_item_id")
    )
    @JsonManagedReference
    private List<QuotationItem> quotationItems;

    private String status;
    private BigDecimal totalAmount;

    private LocalDate quoteDate;
    private LocalDate expiryDate;
    private BigDecimal shippingCharges;

    private Integer discount;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private String notes;
    private String termsAndConditions;

    @Enumerated(EnumType.STRING)
    private PaymentTerms paymentTerms;
}
