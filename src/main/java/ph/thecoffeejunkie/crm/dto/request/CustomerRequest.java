package ph.thecoffeejunkie.crm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ph.thecoffeejunkie.crm.constant.CustomerType;

@Data
public class CustomerRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 100, message = "Preferred shipping method must not exceed 100 characters")
    private String preferredShippingMethod;

    @Size(max = 100, message = "Source must not exceed 100 characters")
    private String source;

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    @Valid
    private BusinessInformationRequest businessInformation;
}
