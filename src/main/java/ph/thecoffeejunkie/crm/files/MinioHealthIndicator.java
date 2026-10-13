package ph.thecoffeejunkie.crm.files;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Part of /actuator/health: the MinIO bucket answers within 2 seconds. */
@Component
class MinioHealthIndicator implements HealthIndicator {

    private final StorageService storage;

    MinioHealthIndicator(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        try {
            // The minio client has no per-call timeout, so bound the call here.
            boolean exists = CompletableFuture.supplyAsync(() -> {
                try {
                    return storage.bucketExists();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).get(2, TimeUnit.SECONDS);
            return exists ? Health.up().build() : Health.down().withDetail("reason", "bucket missing").build();
        } catch (Exception e) {
            return Health.down().build();
        }
    }
}
