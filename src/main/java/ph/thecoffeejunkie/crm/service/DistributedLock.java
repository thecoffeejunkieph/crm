package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cross-instance mutex on a Redis key. Used to make check-then-act sequences (accept a quotation,
 * take a proof-of-payment upload, run a nightly job) happen once even when two requests or two
 * API instances arrive together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLock {

    // Only delete the key if it still holds our token - after our TTL lapses someone else may own it.
    private static final RedisScript<Long> RELEASE = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    // One token per JVM is enough: locks are taken and released by the same instance.
    private static final String OWNER = UUID.randomUUID().toString();

    private final StringRedisTemplate redis;

    public boolean tryLock(String name, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("lock:" + name, OWNER, ttl));
        } catch (RuntimeException e) {
            // Fail open: the callers' own status checks still stop repeats; only the concurrent race reopens.
            log.error("Could not take lock {}; proceeding without it", name, e);
            return true;
        }
    }

    public void unlock(String name) {
        try {
            redis.execute(RELEASE, List.of("lock:" + name), OWNER);
        } catch (RuntimeException e) {
            log.warn("Could not release lock {}; it will expire on its own", name, e);
        }
    }
}
