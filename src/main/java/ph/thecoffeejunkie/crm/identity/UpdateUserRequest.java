package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

/** Null fields are left unchanged; names, when sent, must not be blank. {@code warehouseIds} replaces the assignment. */
public record UpdateUserRequest(@Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String firstName,
                                @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String lastName,
                                String phone, @Size(min = 1, max = 3) Set<@NotNull Role> roles, Set<Long> warehouseIds, long version) {
}
