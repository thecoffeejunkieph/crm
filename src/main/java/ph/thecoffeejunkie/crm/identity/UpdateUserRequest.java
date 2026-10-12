package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.Pattern;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

/** Null fields are left unchanged; names, when sent, must not be blank. {@code warehouseIds} replaces the assignment. */
public record UpdateUserRequest(@Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String firstName,
                                @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String lastName,
                                String phone, Role role, Set<Long> warehouseIds, long version) {
}
