package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SalesRepResponse(
        String email,
        String firstName,
        String lastName
) {}
