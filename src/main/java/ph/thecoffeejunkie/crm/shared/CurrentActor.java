package ph.thecoffeejunkie.crm.shared;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class CurrentActor {

    public Optional<StaffPrincipal> staff() {
        return principal(StaffPrincipal.class);
    }

    public Optional<ContactPrincipal> contact() {
        return principal(ContactPrincipal.class);
    }

    public Scope scope() {
        StaffPrincipal p = staff().orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED, "Login required"));
        return new Scope(p.userId(), p.role(), p.warehouseIds());
    }

    public String clientIp() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest().getRemoteAddr();
    }

    private static <T> Optional<T> principal(Class<T> type) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && type.isInstance(auth.getPrincipal())
                ? Optional.of(type.cast(auth.getPrincipal())) : Optional.empty();
    }
}
