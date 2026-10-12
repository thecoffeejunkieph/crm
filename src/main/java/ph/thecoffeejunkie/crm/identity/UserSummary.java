package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.shared.Role;

public record UserSummary(long id, String email, String displayName, Role role, boolean active) {
}
