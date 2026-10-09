package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import ph.thecoffeejunkie.crm.RedisTestSupport;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedLockIT {

    private final StringRedisTemplate template = RedisTestSupport.template();
    private final DistributedLock lock = new DistributedLock(template);

    @Test
    void secondTryLockFailsUntilUnlock() {
        String name = "test:" + UUID.randomUUID();

        assertTrue(lock.tryLock(name, Duration.ofMinutes(1)));
        assertFalse(lock.tryLock(name, Duration.ofMinutes(1)));

        lock.unlock(name);
        assertTrue(lock.tryLock(name, Duration.ofMinutes(1)));
    }

    @Test
    void lockExpiresAfterTtl() throws InterruptedException {
        String name = "test:" + UUID.randomUUID();

        assertTrue(lock.tryLock(name, Duration.ofMillis(300)));
        Thread.sleep(500);

        assertTrue(lock.tryLock(name, Duration.ofMinutes(1)));
    }

    @Test
    void unlockDoesNotReleaseSomeoneElsesLock() {
        String name = "test:" + UUID.randomUUID();
        assertTrue(lock.tryLock(name, Duration.ofMinutes(1)));
        // Our lock expired and another instance took it over.
        template.opsForValue().set("lock:" + name, "other");

        lock.unlock(name);

        assertTrue(template.hasKey("lock:" + name));
    }

    @Test
    void tryLockFailsOpenWhenRedisDown() {
        var down = new DistributedLock(RedisTestSupport.deadTemplate());

        assertTrue(down.tryLock("anything", Duration.ofMinutes(1)));
    }
}
