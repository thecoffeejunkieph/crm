package ph.thecoffeejunkie.crm.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
public class RedisListenerConfig {

    /**
     * The stock container fails on its first connection attempt and never retries it, so Redis
     * being down at boot would either fail startup or leave live pushes dead until a restart.
     * This one logs and tries again every few seconds; notifications are still stored and reach
     * users on this instance meanwhile (see NotificationStreams.publish).
     */
    @Slf4j
    static class RetryingListenerContainer extends RedisMessageListenerContainer {

        private static final long RETRY_SECONDS = 5;

        private volatile boolean wanted;

        @Override
        public void start() {
            wanted = true;
            attempt();
        }

        @Override
        public void stop() {
            wanted = false;
            super.stop();
        }

        private void attempt() {
            if (!wanted) {
                return;
            }
            try {
                subscribe();
            } catch (RuntimeException e) {
                log.warn("Notification listener could not subscribe to Redis; retrying in {}s: {}",
                        RETRY_SECONDS, e.getMessage());
                super.stop();
                CompletableFuture.delayedExecutor(RETRY_SECONDS, TimeUnit.SECONDS).execute(this::attempt);
                return;
            }
            // stop() may have run while subscribe() was blocked, before there was anything to
            // unsubscribe; undo the late subscription so a shut-down container stays down.
            if (!wanted) {
                super.stop();
            }
        }

        /** Blocks until subscribed or failed; separate so a test can land a stop() in the middle of it. */
        void subscribe() {
            super.start();
        }
    }
}
