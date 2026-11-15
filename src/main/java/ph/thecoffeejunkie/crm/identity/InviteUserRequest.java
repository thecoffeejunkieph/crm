package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

public record InviteUserRequest(@NotBlank @Email String email, @NotBlank String firstName, @NotBlank String lastName,
                                String phone, @NotEmpty Set<@NotNull Role> roles, Set<Long> warehouseIds) {
}
