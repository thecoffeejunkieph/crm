package ph.thecoffeejunkie.crm.shared;

import java.util.Set;

public record Scope(long userId, Role role, Set<Long> warehouseIds) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
