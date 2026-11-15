package ph.thecoffeejunkie.crm.shared;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;
import java.util.Set;

/** Stored in Spring Session (Redis) via JDK serialization, hence Serializable. */
public record StaffPrincipal(long userId, String email, String displayName, Role role, Set<Long> warehouseIds)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return "user:" + userId;
    }
}
