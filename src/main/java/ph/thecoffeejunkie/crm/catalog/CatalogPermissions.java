package ph.thecoffeejunkie.crm.catalog;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.Set;

@Component("catalogPermissions")
public class CatalogPermissions {

    private final CurrentActor actor;

    CatalogPermissions(CurrentActor actor) {
        this.actor = actor;
    }

    public boolean canRead() {
        return is(Role.ADMIN, Role.SALES, Role.WAREHOUSE);
    }

    public boolean canWrite() {
        return is(Role.ADMIN);
    }

    private boolean is(Role... roles) {
        return actor.staff().map(StaffPrincipal::role).map(r -> Set.of(roles).contains(r)).orElse(false);
    }
}
