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

    /** 0 when the request is allowed, otherwise the seconds until the client may try again. */
    public long retryAfterSeconds(String rule, String clientId, int limit, Duration window) {
        long windowSeconds = window.toSeconds();
        long now = Instant.now().getEpochSecond();
        long windowIndex = now / windowSeconds;
        String key = "rl:" + rule + ":" + clientId + ":" + windowIndex;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, window);
            }
            if (count == null || count <= limit) {
                return 0;
            }
            return Math.max(1, (windowIndex + 1) * windowSeconds - now);
        } catch (RuntimeException e) {
            // Fail open: a Redis outage shouldn't lock everyone out of logging in.
            log.warn("Rate limit check failed for rule {}; allowing request", rule, e);
            return 0;
        }
    }
}
