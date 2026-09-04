package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.DiscountType;
import ph.thecoffeejunkie.crm.constant.InvoiceStatus;
import ph.thecoffeejunkie.crm.constant.PaymentTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Invoice extends BaseEntity {

    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @ManyToOne
    @JsonBackReference
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_email")
    private CRMUser salesRep;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "invoice_item_details",
            joinColumns = @JoinColumn(name = "invoice_id"),
            inverseJoinColumns = @JoinColumn(name = "invoice_item_id")
    )
    @JsonManagedReference
    private List<InvoiceItem> invoiceItems;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<InvoicePayment> payments = new ArrayList<>();

    private BigDecimal totalAmount;
    private BigDecimal shippingCharges;

    private Integer discount;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private LocalDate invoiceDate;
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private PaymentTerms paymentTerms;

    private String notes;
    private String termsAndConditions;

    private String proofOfPaymentPath;
    private LocalDateTime paidAt;
}
