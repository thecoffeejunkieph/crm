package ph.thecoffeejunkie.crm.portal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.RequestIdFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Backstop for the revoke-on-change hooks: a contact session ends as soon as the contact stops being active, even if
 * a revocation was missed. Runs after Spring Security's chain so the principal is known (identity needs no reference).
 */
@Configuration
class PortalSessionGuard {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> portalSessionGuardRegistration(Customers customers) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof ContactPrincipal c
                        && !customers.contactActive(c.contactId())) {
                    var session = req.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    SecurityContextHolder.clearContext();
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    res.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,"
                            + "\"detail\":\"Your portal access has ended.\",\"code\":\"UNAUTHORIZED\","
                            + "\"requestId\":\"" + Objects.requireNonNullElse(MDC.get(RequestIdFilter.MDC_KEY), "")
                            + "\"}");
                    return;
                }
                chain.doFilter(req, res);
            }
        };
        var reg = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        reg.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
        return reg;
    }
}
