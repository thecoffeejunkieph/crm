package ph.thecoffeejunkie.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ph.thecoffeejunkie.crm.constant.BusinessType;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessInformation {

    @Column(length = 255)
    private String businessName;

    @Column(length = 20)
    private String tin;

    @Enumerated(EnumType.STRING)
    private BusinessType businessType;
}
