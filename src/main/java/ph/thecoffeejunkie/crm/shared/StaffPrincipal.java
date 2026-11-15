package ph.thecoffeejunkie.crm.shared;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;
import java.util.Set;

/** Stored in Spring Session (Redis) via JDK serialization, hence Serializable. */
public record StaffPrincipal(long userId, String email, String displayName, Set<Role> roles, Set<Long> warehouseIds)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return "user:" + userId;
    }

    public boolean has(Role r) {
        return roles.contains(r);
    }

    /** The role that decides data scoping; see {@link Role#dominant}. */
    public Role role() {
        return Role.dominant(roles);
    }
}
