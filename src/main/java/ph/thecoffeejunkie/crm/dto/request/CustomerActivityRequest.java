package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ph.thecoffeejunkie.crm.constant.ActivityType;

@Data
public class CustomerActivityRequest {

    @NotNull(message = "Activity type is required")
    private ActivityType type;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
