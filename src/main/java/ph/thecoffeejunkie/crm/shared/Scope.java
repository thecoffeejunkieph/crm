package ph.thecoffeejunkie.crm.shared;

import java.util.Set;

public record Scope(long userId, Set<Role> roles, Set<Long> warehouseIds) {

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public boolean has(Role r) {
        return roles.contains(r);
    }

    /** The role that decides data scoping; see {@link Role#dominant}. */
    public Role role() {
        return Role.dominant(roles);
    }
}
