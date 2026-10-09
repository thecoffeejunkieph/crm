package ph.thecoffeejunkie.crm;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;

/** One Redis container shared by every Redis test in the JVM, plus a deliberately unreachable one. */
public final class RedisTestSupport {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static LettuceConnectionFactory deadFactory;

    private RedisTestSupport() {
    }

    public static synchronized LettuceConnectionFactory connectionFactory() {
        if (factory == null) {
            REDIS.start();
            factory = build(REDIS.getHost(), REDIS.getMappedPort(6379));
        }
        return factory;
    }

    /** Port 1 is never listening, so every command fails fast - for fail-open/fail-closed tests. */
    public static synchronized LettuceConnectionFactory deadConnectionFactory() {
        if (deadFactory == null) {
            deadFactory = build("localhost", 1);
        }
        return deadFactory;
    }

    public static StringRedisTemplate template() {
        return new StringRedisTemplate(connectionFactory());
    }

    public static StringRedisTemplate deadTemplate() {
        return new StringRedisTemplate(deadConnectionFactory());
    }

    /** A factory with the same short timeouts as the shared one, for tests that need their own Redis. */
    public static LettuceConnectionFactory build(String host, int port) {
        var clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build())
                        .build())
                .build();
        var f = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfig);
        f.afterPropertiesSet();
        f.start();
        return f;
    }
}
