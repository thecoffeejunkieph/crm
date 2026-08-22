package ph.thecoffeejunkie.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Warehouse extends BaseEntity {

    @NotBlank
    private String name;

    @Column(unique = true)
    private String code;

    private String address;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean defaultWarehouse = false;
}
