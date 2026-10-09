package ph.thecoffeejunkie.crm.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisTimeoutConfigTest {

    /**
     * Lettuce defaults to a 60s command timeout. With Redis down, every request that touches it
     * (all authenticated ones, via revocation) would hang that long and exhaust Tomcat's threads,
     * so "fail open/closed" only works if the timeouts are short.
     */
    @Test
    void redisTimeoutsAreShortInEveryProfile() throws Exception {
        var props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }

        String timeout = props.getProperty("spring.data.redis.timeout");
        String connectTimeout = props.getProperty("spring.data.redis.connect-timeout");

        assertNotNull(timeout, "spring.data.redis.timeout must be set");
        assertNotNull(connectTimeout, "spring.data.redis.connect-timeout must be set");
        assertTrue(Duration.parse("PT" + timeout.toUpperCase()).toSeconds() <= 5, "timeout " + timeout);
        assertTrue(Duration.parse("PT" + connectTimeout.toUpperCase()).toSeconds() <= 5, "connect-timeout " + connectTimeout);
    }
}
