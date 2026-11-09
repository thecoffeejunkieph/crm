package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/**
 * Devices that passed the authenticator skip it at their next login; the password is always checked.
 * The stored key hashes in the password hash and the enrolment time, so changing the password or re-enrolling
 * forgets every device without any bookkeeping. One cookie per user, so people sharing a browser don't evict
 * each other, and a fresh token each time, so a planted cookie is never adopted.
 */
@Component
class TrustedDevices {

    static final String COOKIE_PREFIX = "TCJDEVICE_";
    static final String DEVICE_HEADER = "X-Device-Token";
    private static final Duration IDLE_LIMIT = Duration.ofDays(90);
    /** The browser's own cap; IDLE_LIMIT is what actually ends trust. */
    private static final Duration COOKIE_LIFE = Duration.ofDays(400);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final StringRedisTemplate redis;
    private final boolean secureCookie;

    TrustedDevices(AppUserRepository users, StringRedisTemplate redis,
                   @Value("${app.session.cookie-secure:true}") boolean secureCookie) {
        this.users = users;
        this.redis = redis;
        this.secureCookie = secureCookie;
    }

    /** EXPIRE answers false for a missing key, so this checks and extends the idle window in one step. */
    boolean isTrusted(AppUser u, HttpServletRequest request) {
        String token = HeaderOrCookieSessionIdResolver.isHeaderClient(request)
                ? request.getHeader(DEVICE_HEADER) : cookie(u.id, request);
        return token != null && Boolean.TRUE.equals(redis.expire(key(u, token), IDLE_LIMIT));
    }

    /** Sets the cookie for browsers; returns the token for ANDROID, which keeps it itself. */
    String remember(long userId, HttpServletRequest request, HttpServletResponse response) {
        AppUser u = users.findById(userId).orElseThrow();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(u, token), "1", IDLE_LIMIT);
        if (HeaderOrCookieSessionIdResolver.isHeaderClient(request)) {
            return token;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_PREFIX + u.id, token)
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(COOKIE_LIFE)
                .build().toString());
        return null;
    }

    private static String cookie(long userId, HttpServletRequest request) {
        return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals(COOKIE_PREFIX + userId)).map(Cookie::getValue).findFirst().orElse(null);
    }

    private static String key(AppUser u, String token) {
        return "trusted-device:" + u.id + ":"
                + TotpService.hash(token + "|" + u.passwordHash + "|" + u.totpEnabledAt.toEpochMilli());
    }
}
