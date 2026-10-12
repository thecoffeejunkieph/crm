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

    enum Result { ALREADY_LOCKED, COUNTED, LOCKED }

    /**
     * Atomic: -1 (not counted) while locked; INCR with the TTL set only on creation; at the limit, lock and drop
     * the counter and return 1; otherwise 0. Both keys share a {hash tag} so the script stays on one slot.
     */
    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return -1 end
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

    Result recordFailure(String email) {
        try {
            Long r = redis.execute(RECORD_FAILURE, List.of(failKey(email), lockKey(email)),
                    String.valueOf(WINDOW.toSeconds()), String.valueOf(MAX_FAILURES));
            return r == null || r == 0 ? Result.COUNTED : r == 1 ? Result.LOCKED : Result.ALREADY_LOCKED;
        } catch (RuntimeException e) {
            log.warn("Login throttle count failed", e);
            return Result.COUNTED;
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
        return "login:{" + email + "}:fail";
    }

    private static String lockKey(String email) {
        return "login:{" + email + "}:lock";
    }
}
