package ph.thecoffeejunkie.crm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import ph.thecoffeejunkie.crm.constant.ActivityType;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerActivityResponse(
        Long id,
        ActivityType type,
        String notes,
        LocalDateTime occurredAt,
        SalesRepResponse createdBy
) {}
