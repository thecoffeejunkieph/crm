package ph.thecoffeejunkie.crm.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE connections on this instance, keyed by user id. A user's browser may be connected
 * to any API instance, so notifications go out through Redis pub/sub and every instance delivers
 * to the connections it holds.
 */
@Component
class NotificationStreams implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreams.class);

    static final String CHANNEL_PREFIX = "notifications:user:";

    // EventSource reconnects on its own; a bounded lifetime means a logged-out session's stream
    // does not stay open indefinitely.
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private static final Duration REDIS_RETRY_AFTER = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private volatile long skipRedisUntil;

    NotificationStreams(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    SseEmitter open(long userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // Sent on every (re)connect, so the bell resyncs after any gap in the stream.
        send(userId, emitter, SseEmitter.event().name("unread-count")
                .data("{\"count\":" + unreadCount + "}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    /** Never throws on Redis trouble: the notification still reaches users connected to this instance. */
    void publish(long userId, NotificationResponse notification) {
        String json = jsonMapper.writeValueAsString(notification);
        if (System.currentTimeMillis() < skipRedisUntil) {
            deliver(userId, json);
            return;
        }
        try {
            redis.convertAndSend(CHANNEL_PREFIX + userId, json);
        } catch (RuntimeException e) {
            // One action can notify several users; without this each publish would wait out the
            // Redis timeout in turn while the user who clicked waits for the response.
            skipRedisUntil = System.currentTimeMillis() + REDIS_RETRY_AFTER.toMillis();
            log.warn("Could not publish notification {} to Redis; delivering on this instance only for the next {}s: {}",
                    notification.id(), REDIS_RETRY_AFTER.toSeconds(), e.getMessage());
            deliver(userId, json);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        deliver(Long.parseLong(channel.substring(CHANNEL_PREFIX.length())),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }

    /** Keeps proxies from closing idle streams and finds connections that are already gone. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emitters.forEach((userId, set) -> set.forEach(emitter ->
                send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    private void deliver(long userId, String json) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        String id = jsonMapper.readTree(json).path("id").asString();
        set.forEach(emitter -> send(userId, emitter, SseEmitter.event().id(id).name("notification")
                .data(json, MediaType.APPLICATION_JSON)));
    }

    private void send(long userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
    }

    private void remove(long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
