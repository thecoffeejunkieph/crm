package ph.thecoffeejunkie.crm.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Honours an optional Idempotency-Key header on create endpoints: a retried or double-clicked POST
 * with the same key gets the first response replayed instead of creating a second record.
 * Registered as a plain servlet filter, so it runs after Spring Security and knows the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final List<String> PATHS = List.of(
            "/api/v1/quotations",
            "/api/v1/invoices",
            "/api/v1/invoices/*/payments",
            "/api/v1/inventory/receive",
            "/api/v1/inventory/reserve",
            "/api/v1/inventory/release"
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String HEADER = "Idempotency-Key";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 100;

    private final StringRedisTemplate redis;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod())
                || request.getHeader(HEADER) == null
                || PATHS.stream().noneMatch(p -> PATH_MATCHER.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER);
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            writeJson(response, 400, "Bad Request", "Idempotency-Key must be 1 to " + MAX_KEY_LENGTH + " characters.");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String redisKey = "idem:" + currentUser() + ":POST:" + path + ":" + idempotencyKey;

        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, TTL);
        } catch (RuntimeException e) {
            log.warn("Idempotency store unavailable; processing {} without it", path, e);
            chain.doFilter(request, response);
            return;
        }

        if (!Boolean.TRUE.equals(acquired)) {
            String stored = redis.opsForValue().get(redisKey);
            if (IN_PROGRESS.equals(stored)) {
                writeJson(response, 409, "Conflict", "A request with this Idempotency-Key is still being processed.");
                return;
            }
            if (stored != null) {
                replay(stored, response);
                return;
            }
            // Expired between the two calls - nothing to replay, so just process it.
            chain.doFilter(request, response);
            return;
        }

        var wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } catch (IOException | ServletException | RuntimeException e) {
            redis.delete(redisKey);
            throw e;
        }
        remember(redisKey, wrapper);
        wrapper.copyBodyToResponse();
    }

    /** Successes are kept for replay; failures release the key so the client can retry. */
    private void remember(String redisKey, ContentCachingResponseWrapper wrapper) {
        try {
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                String contentType = wrapper.getContentType() == null ? "" : wrapper.getContentType();
                String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                redis.opsForValue().set(redisKey, status + "\n" + contentType + "\n" + body, TTL);
            } else {
                redis.delete(redisKey);
            }
        } catch (RuntimeException e) {
            log.warn("Could not store idempotent response for {}", redisKey, e);
        }
    }

    private static void replay(String stored, HttpServletResponse response) throws IOException {
        String[] parts = stored.split("\n", 3);
        response.setStatus(Integer.parseInt(parts[0]));
        if (!parts[1].isEmpty()) {
            response.setContentType(parts[1]);
        }
        response.setHeader("Idempotent-Replayed", "true");
        response.getOutputStream().write(parts[2].getBytes(StandardCharsets.UTF_8));
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth instanceof AnonymousAuthenticationToken ? "anonymous" : auth.getName();
    }

    private static void writeJson(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
