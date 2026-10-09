package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Makes logout real for stateless JWTs: a logged-out token's id goes on a denylist until it
 * would have expired anyway, and "log out everywhere" records a cut-off time per user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    // Matches the token lifetime in JwtUtil - after this every token issued before the cut-off is dead anyway.
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofHours(10);

    private final StringRedisTemplate redis;

    public void revoke(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        redis.opsForValue().set("auth:deny:" + jti, "1", remaining);
    }

    public void revokeAllFor(String username) {
        redis.opsForValue().set("auth:revoked-before:" + username,
                String.valueOf(Instant.now().getEpochSecond()), MAX_TOKEN_LIFETIME);
    }

    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey("auth:deny:" + jti))) {
                return true;
            }
            String revokedBefore = redis.opsForValue().get("auth:revoked-before:" + username);
            // <= on whole seconds: a login in the same second as "log out everywhere" is also cut off.
            return revokedBefore != null && issuedAt.getEpochSecond() <= Long.parseLong(revokedBefore);
        } catch (RuntimeException e) {
            // Fail closed: without Redis we can't tell a logged-out token from a live one.
            log.error("Token revocation check failed; treating token as revoked", e);
            return true;
        }
    }
}
