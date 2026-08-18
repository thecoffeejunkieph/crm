package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.CustomerType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse (
        Long id,
        String firstName,
        String lastName,
        String email,
        String address,
        String phoneNumber,
        String preferredShippingMethod,
        String source,
        CustomerType customerType,
        BusinessInformationResponse businessInformation
){}
