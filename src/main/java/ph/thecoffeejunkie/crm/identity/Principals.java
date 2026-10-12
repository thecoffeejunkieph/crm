package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.Set;

final class Principals {

    private static final HttpSessionSecurityContextRepository REPOSITORY = new HttpSessionSecurityContextRepository();

    private Principals() {
    }

    static StaffPrincipal staffOf(AppUser u, Set<Long> warehouseIds) {
        // Set.copyOf: the principal is JDK-serialized into Redis, so no Hibernate collection proxies.
        return new StaffPrincipal(u.id, u.email, u.displayName(), u.role, Set.copyOf(warehouseIds));
    }

    /** Rotates the session id (fixation protection) and stores the authentication in the session. */
    static void establish(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
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
