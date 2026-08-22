package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ph.thecoffeejunkie.crm.constant.BusinessType;

@Data
public class BusinessInformationRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name must not exceed 255 characters")
    private String businessName;

    @NotBlank(message = "TIN is required")
    @Size(max = 20, message = "TIN must not exceed 20 characters")
    private String tin;

    @NotNull(message = "Business type is required")
    private BusinessType businessType;
}
