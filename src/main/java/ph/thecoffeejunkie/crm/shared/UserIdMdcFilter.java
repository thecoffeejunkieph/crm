package ph.thecoffeejunkie.crm.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs after Spring Security's chain so the authenticated principal is known. */
@Configuration
class UserIdMdcFilter {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> userIdMdcRegistration() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                boolean set = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
                if (set) {
                    MDC.put("userId", auth.getName());
                }
                try {
                    chain.doFilter(req, res);
                } finally {
                    if (set) {
                        MDC.remove("userId");
                    }
                }
            }
        };
        var reg = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        reg.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return reg;
    }
}
