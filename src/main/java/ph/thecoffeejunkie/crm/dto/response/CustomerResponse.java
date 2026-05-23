package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse (
        Long id,
        String firstName,
        String lastName,
        String email,
        String address,
        String phoneNumber
){}
