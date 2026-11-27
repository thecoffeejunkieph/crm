package ph.thecoffeejunkie.crm.shared;

import java.util.Collection;

/** Declared from most to least privileged: {@link #dominant} relies on this order. */
public enum Role {
    ADMIN, SALES, WAREHOUSE;

    /**
     * The role that decides data scoping for a user holding {@code roles}: an admin who is also a rep keeps
     * admin visibility. SALES with WAREHOUSE is rejected at user admin, so the two scopings never merge.
     */
    public static Role dominant(Collection<Role> roles) {
        return roles.stream().min(Enum::compareTo)
                .orElseThrow(() -> new IllegalStateException("User holds no role"));
    }
}
