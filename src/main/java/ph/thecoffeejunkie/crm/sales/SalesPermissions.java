package ph.thecoffeejunkie.crm.sales;

import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.Set;

@Component("salesPermissions")
public class SalesPermissions {

    private final CurrentActor actor;

    SalesPermissions(CurrentActor actor) {
        this.actor = actor;
    }

    public boolean canRead() {
        return is(Role.ADMIN, Role.SALES);
    }

    public boolean canWrite() {
        return is(Role.ADMIN, Role.SALES);
    }

    /** Only an admin can confirm or reject a payment; it is what releases an order to the warehouse. */
    public boolean canVerifyPayments() {
        return is(Role.ADMIN);
    }

    private boolean is(Role... roles) {
        return actor.staff().map(StaffPrincipal::role).map(r -> Set.of(roles).contains(r)).orElse(false);
    }
}
