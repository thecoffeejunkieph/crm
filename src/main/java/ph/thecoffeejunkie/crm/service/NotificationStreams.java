package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ph.thecoffeejunkie.crm.dto.response.NotificationResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE connections on this instance, keyed by user email. A user's browser may be connected
 * to any API instance, so notifications go out through Redis pub/sub and every instance delivers
 * to the connections it holds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreams implements MessageListener {

    public static final String CHANNEL_PREFIX = "notify:";

    // EventSource reconnects on its own; a bounded lifetime means a logged-out token's stream
    // doesn't stay open indefinitely.
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    private static final Duration REDIS_RETRY_AFTER = Duration.ofSeconds(10);

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private volatile long skipRedisUntil;

    public SseEmitter open(String email, long unreadCount) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(email, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(email, emitter));
        emitter.onTimeout(() -> remove(email, emitter));
        emitter.onError(e -> remove(email, emitter));

        // Sent on every (re)connect, so the bell resyncs after any gap in the stream.
        send(email, emitter, SseEmitter.event().name("unread-count")
                .data("{\"count\":" + unreadCount + "}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    /** Never throws: with Redis down the notification still reaches users connected to this instance. */
    public void publish(String email, NotificationResponse notification) {
        String json = jsonMapper.writeValueAsString(notification);
        if (System.currentTimeMillis() < skipRedisUntil) {
            deliver(email, json);
            return;
        }
        try {
            redis.convertAndSend(CHANNEL_PREFIX + email, json);
        } catch (RuntimeException e) {
            // One action can notify several users; without this each publish would wait out the
            // Redis timeout in turn while the user who clicked waits for the response.
            skipRedisUntil = System.currentTimeMillis() + REDIS_RETRY_AFTER.toMillis();
            log.warn("Could not publish notification {} to Redis; delivering on this instance only for the next {}s: {}",
                    notification.id(), REDIS_RETRY_AFTER.toSeconds(), e.getMessage());
            deliver(email, json);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        deliver(channel.substring(CHANNEL_PREFIX.length()), new String(message.getBody(), StandardCharsets.UTF_8));
    }

    /** Keeps proxies (Traefik) from closing idle streams and finds connections that are already gone. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((email, set) -> set.forEach(emitter ->
                send(email, emitter, SseEmitter.event().comment("ping"))));
    }

    private void deliver(String email, String json) {
        Set<SseEmitter> set = emitters.get(email);
        if (set == null || set.isEmpty()) {
            return;
        }
        String id = jsonMapper.readTree(json).path("id").asString();
        set.forEach(emitter -> send(email, emitter, SseEmitter.event().id(id).name("notification")
                .data(json, MediaType.APPLICATION_JSON)));
    }

    private void send(String email, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            remove(email, emitter);
        }
    }

    private void remove(String email, SseEmitter emitter) {
        emitters.computeIfPresent(email, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
