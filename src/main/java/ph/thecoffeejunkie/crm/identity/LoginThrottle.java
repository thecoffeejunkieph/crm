package ph.thecoffeejunkie.crm.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Failed-login lockout per normalised email, identical for known and unknown emails so ACCOUNT_LOCKED
 * reveals nothing. Fails open (WARN) when Redis is down, like RateLimiter.
 */
@Slf4j
@Component
class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Atomic: no counting while locked; INCR with the TTL set only on creation; at the limit, lock and drop
     * the counter. Returns 1 only for the call that locked.
     */
    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            if c >= tonumber(ARGV[2]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    LoginThrottle(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean isLocked(String email) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(lockKey(email)));
        } catch (RuntimeException e) {
            log.warn("Login throttle check failed; allowing attempt", e);
            return false;
        }
    }

    /** True if this failure locked the email. */
    boolean recordFailure(String email) {
        try {
            Long locked = redis.execute(RECORD_FAILURE, List.of(failKey(email), lockKey(email)),
                    String.valueOf(WINDOW.toSeconds()), String.valueOf(MAX_FAILURES));
            return locked != null && locked == 1;
        } catch (RuntimeException e) {
            log.warn("Login throttle count failed", e);
            return false;
        }
    }

    void clear(String email) {
        try {
            redis.delete(List.of(failKey(email), lockKey(email)));
        } catch (RuntimeException e) {
            log.warn("Login throttle clear failed", e);
        }
    }

    private static String failKey(String email) {
        return "login:fail:" + email;
    }

    private static String lockKey(String email) {
        return "login:lock:" + email;
    }
}
