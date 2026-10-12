package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import ph.thecoffeejunkie.crm.RedisTestSupport;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CacheConfigIT {

    /** Stand-in for a dashboard service: counts how often the real work runs. */
    static class SummarySource {
        private final AtomicInteger calls = new AtomicInteger();
        private final String run = UUID.randomUUID().toString(); // keeps keys unique across test runs

        // Accessors, not fields: the bean is a CGLIB proxy whose own fields are never set.
        public int calls() {
            return calls.get();
        }

        public String run() {
            return run;
        }

        @Cacheable("dashboard-summary")
        public String summary(String runId, LocalDate from, LocalDate to) {
            calls.incrementAndGet();
            return "summary " + from + ".." + to;
        }
    }

    @Test
    void secondCallIsServedFromCache() {
        try (var ctx = context(RedisTestSupport.connectionFactory())) {
            var source = ctx.getBean(SummarySource.class);
            var from = LocalDate.of(2026, 10, 1);
            var to = LocalDate.of(2026, 10, 31);

            source.summary(source.run(), from, to);
            awaitCached(ctx, new SimpleKey(source.run(), from, to));
            String second = source.summary(source.run(), from, to);
            assertEquals(1, source.calls());
            assertEquals("summary 2026-10-01..2026-10-31", second);

            source.summary(source.run(), from, to.minusDays(1));
            assertEquals(2, source.calls());
        }
    }

    /** The write that follows the first call must be visible before we assert the second call hits it. */
    private static void awaitCached(AnnotationConfigApplicationContext ctx, Object key) {
        var cache = ctx.getBean(RedisCacheManager.class).getCache("dashboard-summary");
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (cache.get(key) == null && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertNotNull(cache.get(key), "first call's result was never written to Redis");
    }

    @Test
    void cacheErrorsFallThroughToMethod() {
        try (var ctx = context(RedisTestSupport.deadConnectionFactory())) {
            var source = ctx.getBean(SummarySource.class);

            String result = source.summary(source.run(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertEquals("summary 2026-01-01..2026-01-31", result);
            assertEquals(1, source.calls());
        }
    }

    private static AnnotationConfigApplicationContext context(RedisConnectionFactory factory) {
        var ctx = new AnnotationConfigApplicationContext();
        ctx.register(CacheConfig.class);
        ctx.registerBean(RedisCacheManager.class, () -> RedisCacheManager.builder(factory)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(60)))
                .build());
        ctx.registerBean(SummarySource.class);
        ctx.refresh();
        return ctx;
    }
}
