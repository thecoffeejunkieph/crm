package ph.thecoffeejunkie.crm.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.Locale;
import java.util.Set;

/**
 * Seeds the first admin on an empty install: set app.bootstrap.admin-email and the invite goes to that address.
 * Recovery: if that admin is still waiting to accept (lost or failed mail) and no other admin can log in, each
 * start issues a fresh invite, which invalidates the old link.
 */
@Slf4j
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
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        try {
            if (users.count() == 0) {
                admin.invite(new InviteUserRequest(adminEmail, "Admin", "User", null, Role.ADMIN, Set.of()));
            } else if (!users.existsByRoleAndArchivedAtIsNullAndPasswordHashIsNotNull(Role.ADMIN)) {
                users.findByEmailAndArchivedAtIsNull(adminEmail.trim().toLowerCase(Locale.ROOT))
                        .filter(u -> u.role == Role.ADMIN && u.passwordHash == null)
                        .ifPresent(u -> admin.resendInvite(u.id));
            }
        } catch (DomainException e) {
            // Another instance bootstrapped at the same moment; its invite stands.
            if (e.code() != ErrorCode.DUPLICATE) {
                throw e;
            }
            log.info("Bootstrap admin already created by another instance");
        }
    }
}
