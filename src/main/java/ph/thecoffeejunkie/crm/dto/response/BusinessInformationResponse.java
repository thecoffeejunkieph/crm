package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.BusinessType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BusinessInformationResponse (
        String businessName,
        String tin,
        BusinessType businessType
){}
