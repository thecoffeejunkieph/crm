package ph.thecoffeejunkie.crm.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.dto.response.DashboardSummaryResponse;
import ph.thecoffeejunkie.crm.dto.response.SalesSummaryResponse;
import ph.thecoffeejunkie.crm.dto.response.WarehouseDashboardSummaryResponse;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            String second = source.summary(source.run(), from, to);
            assertEquals(1, source.calls());
            assertEquals("summary 2026-10-01..2026-10-31", second);

            source.summary(source.run(), from, to.minusDays(1));
            assertEquals(2, source.calls());
        }
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

    @Test
    void dashboardResponsesAreSerializable() {
        List<String> notSerializable = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> root : List.of(DashboardSummaryResponse.class, SalesSummaryResponse.class,
                WarehouseDashboardSummaryResponse.class)) {
            collect(root, seen, notSerializable);
        }
        assertTrue(notSerializable.isEmpty(), "Not Serializable (JDK cache serialization needs it): " + notSerializable);
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

    private static void collect(Type type, Set<Class<?>> seen, List<String> bad) {
        if (type instanceof ParameterizedType p) {
            for (Type arg : p.getActualTypeArguments()) {
                collect(arg, seen, bad);
            }
            return;
        }
        if (!(type instanceof Class<?> c) || !c.getName().startsWith("ph.thecoffeejunkie.crm") || !seen.add(c)) {
            return;
        }
        if (!Serializable.class.isAssignableFrom(c)) {
            bad.add(c.getSimpleName());
        }
        if (c.isRecord()) {
            for (RecordComponent rc : c.getRecordComponents()) {
                collect(rc.getGenericType(), seen, bad);
            }
        }
    }
}
