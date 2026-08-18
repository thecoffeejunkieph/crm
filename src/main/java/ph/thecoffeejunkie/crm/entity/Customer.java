package ph.thecoffeejunkie.crm.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import ph.thecoffeejunkie.crm.constant.CustomerType;

import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String preferredShippingMethod;

    @Column(length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    private CustomerType customerType;

    @Embedded
    private BusinessInformation businessInformation;

    @OneToMany
    @JoinTable(
            name = "customer_quotation",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "quotation_id")
    )
    @JsonManagedReference
    private List<Quotation> quotation;
}
