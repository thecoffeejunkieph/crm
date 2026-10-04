package ph.thecoffeejunkie.crm.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryOrder extends BaseEntity {

    @Column(unique = true)
    private String deliveryOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    private DeliveryOrderStatus status;

    private String deliveryAddress;
    private String deliveryInstructions;
    private LocalDate targetDeliveryDate;

    @ElementCollection
    @CollectionTable(name = "delivery_order_proof_of_pickup", joinColumns = @JoinColumn(name = "delivery_order_id"))
    @Column(name = "file_path")
    private List<String> proofOfPickupPaths = new ArrayList<>();
    private LocalDateTime pickedUpAt;

    @ElementCollection
    @CollectionTable(name = "delivery_order_proof_of_delivery", joinColumns = @JoinColumn(name = "delivery_order_id"))
    @Column(name = "file_path")
    private List<String> proofOfDeliveryPaths = new ArrayList<>();
    private LocalDateTime deliveredAt;
}
