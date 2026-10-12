package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/** Spring Session only expires idle sessions; staff sessions also end 12h after they were created. */
class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    static final Duration MAX_AGE = Duration.ofHours(12);

    private final Clock clock;

    AbsoluteSessionTimeoutFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (session != null && auth != null && auth.getPrincipal() instanceof StaffPrincipal
                && clock.millis() - session.getCreationTime() > MAX_AGE.toMillis()) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            SecurityConfig.writeProblem(response, ErrorCode.UNAUTHORIZED, "Your session has expired. Log in again.");
            return;
        }
        chain.doFilter(request, response);
    }
}
