package ph.thecoffeejunkie.crm.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ph.thecoffeejunkie.crm.service.RateLimiter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-IP limits on the endpoints worth brute-forcing: login and the token links in customer emails.
 * The client IP comes from getRemoteAddr(); behind Traefik, server.forward-headers-strategy=native
 * makes that the real client address (only trusted internal proxies' X-Forwarded-For is honoured).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String pattern, int limit, Duration window) {}

    private static final List<Rule> RULES = List.of(
            new Rule("login", "/api/v1/auth/login", 10, Duration.ofSeconds(60)),
            new Rule("quote-link", "/api/v1/quotations/*/respond", 20, Duration.ofSeconds(60)),
            new Rule("payment-link", "/api/v1/invoices/*/proof-of-payment", 20, Duration.ofSeconds(60))
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        for (Rule rule : RULES) {
            if (!PATH_MATCHER.match(rule.pattern(), path)) {
                continue;
            }
            long retryAfter = rateLimiter.retryAfterSeconds(rule.name(), request.getRemoteAddr(), rule.limit(), rule.window());
            if (retryAfter > 0) {
                log.warn("Rate limit '{}' hit by {} on {}", rule.name(), request.getRemoteAddr(), path);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many attempts. Try again in "
                        + retryAfter + " seconds.\"}");
                return;
            }
            break;
        }
        chain.doFilter(request, response);
    }
}
