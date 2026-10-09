package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window counter in Redis, shared by every API instance.
 * ponytail: fixed window allows up to 2x the limit across a window edge; sliding log if that ever matters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redis;

    /** Counts this request, then: 0 when allowed, otherwise the seconds until the client may try again. */
    public long retryAfterSeconds(String rule, String clientId, int limit, Duration window) {
        long retryAfter = retryAfterIfOver(rule, clientId, limit, window);
        if (retryAfter == 0) {
            record(rule, clientId, window);
        }
        return retryAfter;
    }

    /** Read-only: blocked once `limit` hits are recorded. For rules that only count some outcomes (failed logins). */
    public long retryAfterIfOver(String rule, String clientId, int limit, Duration window) {
        long windowSeconds = window.toSeconds();
        long now = Instant.now().getEpochSecond();
        try {
            String count = redis.opsForValue().get(key(rule, clientId, window));
            if (count == null || Long.parseLong(count) < limit) {
                return 0;
            }
            return Math.max(1, (now / windowSeconds + 1) * windowSeconds - now);
        } catch (RuntimeException e) {
            // Fail open: a Redis outage shouldn't lock everyone out of logging in.
            log.warn("Rate limit check failed for rule {}; allowing request", rule, e);
            return 0;
        }
    }

    public void record(String rule, String clientId, Duration window) {
        String key = key(rule, clientId, window);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, window);
            }
        } catch (RuntimeException e) {
            log.warn("Rate limit count failed for rule {}", rule, e);
        }
    }

    private static String key(String rule, String clientId, Duration window) {
        return "rl:" + rule + ":" + clientId + ":" + Instant.now().getEpochSecond() / window.toSeconds();
    }
}
