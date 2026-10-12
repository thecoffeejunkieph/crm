package ph.thecoffeejunkie.crm.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Set;

/** Seeds the first admin on an empty install: set app.bootstrap.admin-email and the invite goes to that address. */
@Component
class BootstrapAdmin {

    private final AppUserRepository users;
    private final UserAdminService admin;
    private final String email;

    BootstrapAdmin(AppUserRepository users, UserAdminService admin,
                   @Value("${app.bootstrap.admin-email:}") String email) {
        this.users = users;
        this.admin = admin;
        this.email = email;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        bootstrap(email);
    }

    void bootstrap(String adminEmail) {
        if (adminEmail == null || adminEmail.isBlank() || users.count() > 0) {
            return;
        }
        admin.invite(new InviteUserRequest(adminEmail, "Admin", "User", null, Role.ADMIN, Set.of()));
    }
}
