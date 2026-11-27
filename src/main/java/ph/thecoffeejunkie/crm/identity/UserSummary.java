package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

public record UserSummary(long id, String email, String displayName, Set<Role> roles, boolean active) {
}
