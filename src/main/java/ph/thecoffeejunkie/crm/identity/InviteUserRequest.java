package ph.thecoffeejunkie.crm.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

public record InviteUserRequest(@NotBlank @Email String email, @NotBlank String firstName, @NotBlank String lastName,
                                String phone, @NotNull Role role, Set<Long> warehouseIds) {
}
