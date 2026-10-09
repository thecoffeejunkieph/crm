package ph.thecoffeejunkie.crm.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.testcontainers.containers.GenericContainer;
import ph.thecoffeejunkie.crm.RedisTestSupport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisListenerConfigIT {

    @Test
    void stopDuringSubscribeLeavesContainerStopped() throws Exception {
        // Models shutdown landing while an attempt is inside super.start(): the flag flips before
        // the subscription completes, so the container's own stop() had nothing to unsubscribe yet.
        var container = new RedisListenerConfig.RetryingListenerContainer() {
            @Override
            void subscribe() {
                stop();
                super.subscribe();
            }
        };
        container.setConnectionFactory(RedisTestSupport.connectionFactory());
        container.addMessageListener((message, pattern) -> { }, new PatternTopic("notify:*"));
        container.afterPropertiesSet();

        container.start();

        assertFalse(container.isRunning());
        assertFalse(container.isListening());
        container.destroy();
    }

    @Test
    void subscribesOnceRedisComesBack() throws Exception {
        try (var redis = new GenericContainer<>("redis:7.4.2-alpine").withExposedPorts(6379)) {
            redis.start();
            var factory = RedisTestSupport.build(redis.getHost(), redis.getMappedPort(6379));
            var received = new CountDownLatch(1);
            MessageListener listener = (message, pattern) -> received.countDown();
            var container = new RedisListenerConfig.RetryingListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener(listener, new PatternTopic("notify:*"));
            container.afterPropertiesSet();

            redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
            container.start(); // fails while Redis is unresponsive and schedules a retry
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();

            long deadline = System.currentTimeMillis() + 20_000;
            while (!container.isListening() && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
            new StringRedisTemplate(factory).convertAndSend("notify:a@t.ph", "{}");

            assertTrue(received.await(5, TimeUnit.SECONDS), "listener never received a message after recovery");
            container.stop();
            container.destroy();
            factory.destroy();
        }
    }
}
