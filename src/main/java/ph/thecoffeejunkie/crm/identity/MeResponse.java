package ph.thecoffeejunkie.crm.identity;

import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

record MeResponse(long id, String email, String firstName, String lastName, String phone, SortedSet<Role> roles,
                  List<Long> warehouseIds, boolean totpEnabled, String avatarUrl) {

    static MeResponse of(AppUser u) {
        return new MeResponse(u.id, u.email, u.firstName, u.lastName, u.phone, new TreeSet<>(u.roles),
                u.warehouseIds.stream().sorted().toList(), u.totpEnabledAt != null, StorageService.url(u.avatarKey));
    }
}
