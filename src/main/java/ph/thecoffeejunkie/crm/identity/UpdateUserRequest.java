package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

/** Null fields are left unchanged; {@code warehouseIds} replaces the whole assignment when present. */
public record UpdateUserRequest(String firstName, String lastName, String phone, Role role, Set<Long> warehouseIds,
                                long version) {
}
