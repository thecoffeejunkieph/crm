package ph.thecoffeejunkie.crm.shared;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;

/** Stored in Spring Session (Redis) via JDK serialization, hence Serializable. */
public record ContactPrincipal(long contactId, long customerId, String email, String displayName)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return "contact:" + contactId;
    }
}
