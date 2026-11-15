package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.Set;

public final class Principals {

    private static final HttpSessionSecurityContextRepository REPOSITORY = new HttpSessionSecurityContextRepository();

    private Principals() {
    }

    static StaffPrincipal staffOf(AppUser u, Set<Long> warehouseIds) {
        // Set.copyOf: the principal is JDK-serialized into Redis, so no Hibernate collection proxies.
        return new StaffPrincipal(u.id, u.email, u.displayName(), u.role, Set.copyOf(warehouseIds));
    }

    /** A portal session's 7-day idle interval must not carry over to whoever signs in next on this browser. */
    private static void dropContactSession(HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null && old.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                instanceof SecurityContext c && c.getAuthentication() != null
                && c.getAuthentication().getPrincipal() instanceof ContactPrincipal) {
            old.invalidate();
        }
    }

    /** Rotates the session id (fixation protection) and stores the authentication in the session. */
    public static void establish(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        dropContactSession(request);
        request.getSession(true);
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        REPOSITORY.saveContext(context, request, response);
        HttpSession session = request.getSession();
        session.setAttribute("clientType", HeaderOrCookieSessionIdResolver.ANDROID.equals(
                request.getHeader(HeaderOrCookieSessionIdResolver.CLIENT_TYPE_HEADER)) ? "ANDROID" : "WEB");
        session.setAttribute("clientIp", request.getRemoteAddr());
        session.setAttribute("userAgent", request.getHeader("User-Agent"));
    }
}
