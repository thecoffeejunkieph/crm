package ph.thecoffeejunkie.crm.customers;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.Set;

@Component("customerPermissions")
public class CustomerPermissions {

    private final CurrentActor actor;

    CustomerPermissions(CurrentActor actor) {
        this.actor = actor;
    }

    public boolean canRead() {
        return is(Role.ADMIN, Role.SALES);
    }

    public boolean canCreate() {
        return is(Role.ADMIN, Role.SALES);
    }

    public boolean canEdit() {
        return is(Role.ADMIN, Role.SALES);
    }

    public boolean canArchive() {
        return is(Role.ADMIN);
    }

    public boolean canAssign() {
        return is(Role.ADMIN);
    }

    private boolean is(Role... roles) {
        return actor.staff().map(StaffPrincipal::role).map(r -> Set.of(roles).contains(r)).orElse(false);
    }
}
