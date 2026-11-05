package ph.thecoffeejunkie.crm.leads;

import jakarta.validation.constraints.NotNull;

public record LeadAssignRequest(Long repId, @NotNull Long version) {
}
