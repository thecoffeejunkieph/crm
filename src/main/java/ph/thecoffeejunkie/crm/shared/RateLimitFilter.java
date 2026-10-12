package ph.thecoffeejunkie.crm.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

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
public class RateLimitFilter extends OncePerRequestFilter {

    /** failuresOnly: count only requests answered 401, so a whole office on one NAT IP can still log in. */
    record Rule(String name, String pattern, int limit, Duration window, boolean failuresOnly) {}

    // Task 15 fills the real rules.
    private static final List<Rule> DEFAULT_RULES = List.of();

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimiter rateLimiter;
    private final List<Rule> rules;

    @Autowired
    public RateLimitFilter(RateLimiter rateLimiter) {
        this(rateLimiter, DEFAULT_RULES);
    }

    RateLimitFilter(RateLimiter rateLimiter, List<Rule> rules) {
        this.rateLimiter = rateLimiter;
        this.rules = rules;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        for (Rule rule : rules) {
            if (!PATH_MATCHER.match(rule.pattern(), path)) {
                continue;
            }
            String client = request.getRemoteAddr();
            long retryAfter = rule.failuresOnly()
                    ? rateLimiter.retryAfterIfOver(rule.name(), client, rule.limit(), rule.window())
                    : rateLimiter.retryAfterSeconds(rule.name(), client, rule.limit(), rule.window());
            if (retryAfter > 0) {
                log.warn("Rate limit '{}' hit by {} on {}", rule.name(), request.getRemoteAddr(), path);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many attempts. Try again in "
                        + retryAfter + " seconds.\"}");
                return;
            }
            chain.doFilter(request, response);
            if (rule.failuresOnly() && response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                rateLimiter.record(rule.name(), client, rule.window());
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
