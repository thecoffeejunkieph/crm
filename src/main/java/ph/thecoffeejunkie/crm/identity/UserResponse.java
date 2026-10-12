package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.util.List;

public record UserResponse(long id, String email, String firstName, String lastName, String phone, Role role,
                           List<Long> warehouseIds, boolean totpEnabled, boolean invitePending,
                           String inviteEmailStatus, Instant archivedAt, long version) {

    static UserResponse of(AppUser u) {
        return new UserResponse(u.id, u.email, u.firstName, u.lastName, u.phone, u.role,
                u.warehouseIds.stream().sorted().toList(), u.totpEnabledAt != null,
                u.passwordHash == null && u.archivedAt == null, u.inviteEmailStatus, u.archivedAt, u.version);
    }
}
