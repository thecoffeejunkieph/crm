package ph.thecoffeejunkie.crm.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * One line per finished request (spec 2026-11-05 logging): method, path, status, time and who. Query parameter names
 * only, never their values. 4xx warn with the ProblemDetail code; 5xx error (the stack is logged by the advice).
 */
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String PROBLEM_CODE = "crm.problemCode";
    private static final Logger log = LoggerFactory.getLogger("crm.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/api/notifications/stream") || !path.startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
            write(req, res.getStatus(), (System.nanoTime() - start) / 1_000_000);
        } catch (IOException | ServletException | RuntimeException e) {
            // The container turns an escaped exception into a 500 only after we return.
            write(req, 500, (System.nanoTime() - start) / 1_000_000);
            throw e;
        }
    }

    private void write(HttpServletRequest req, int status, long ms) {
        try {
            List<String> params = Collections.list(req.getParameterNames());
            StringBuilder line = new StringBuilder().append(req.getMethod()).append(' ').append(req.getRequestURI())
                    .append(" → ").append(status).append(" in ").append(ms).append("ms user=")
                    .append(user()).append(' ').append(role());
            if (!params.isEmpty()) {
                line.append(" q=").append(params);
            }
            Object code = req.getAttribute(PROBLEM_CODE);
            if (code != null) {
                line.append(" code=").append(code);
            }
            if (status >= 500) {
                log.error("{}", line);
            } else if (status >= 400) {
                log.warn("{}", line);
            } else {
                log.info("{}", line);
            }
        } catch (RuntimeException e) {
            log.warn("log-render-failed {}", e.toString());
        }
    }

    private static String user() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName()) ? "-" : a.getName();
    }

    private static String role() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName())) {
            return "-";
        }
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(r -> r.startsWith("ROLE_"))
                .map(r -> r.substring(5)).findFirst().orElse("-");
    }

    @Configuration
    static class Registration {

        @Bean
        AccessLogFilter accessLogFilter() {
            return new AccessLogFilter();
        }

        /** Runs inside the security chain (see SecurityConfig), not as a separate servlet filter. */
        @Bean
        FilterRegistrationBean<AccessLogFilter> accessLogRegistration(AccessLogFilter filter) {
            var reg = new FilterRegistrationBean<>(filter);
            reg.setEnabled(false);
            return reg;
        }
    }
}
