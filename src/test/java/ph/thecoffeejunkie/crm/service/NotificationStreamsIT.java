package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.config.RedisListenerConfig;
import ph.thecoffeejunkie.crm.constant.NotificationType;
import ph.thecoffeejunkie.crm.dto.response.NotificationResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class NotificationStreamsIT {

    @RestController
    static class StreamController {
        private final NotificationStreams streams;

        StreamController(NotificationStreams streams) {
            this.streams = streams;
        }

        @GetMapping("/stream")
        SseEmitter stream(@RequestParam String email, @RequestParam(defaultValue = "0") long unread) {
            return streams.open(email, unread);
        }
    }

    private final List<RedisMessageListenerContainer> containers = new ArrayList<>();

    @AfterEach
    void stopContainers() {
        containers.forEach(c -> {
            c.stop();
            try {
                c.destroy();
            } catch (Exception ignored) {
            }
        });
    }

    @Test
    void openSendsUnreadCountFirst() throws Exception {
        var streams = streams(RedisTestSupport.template());

        MvcResult result = open(mvc(streams), email(), 3);

        assertTrue(result.getResponse().getContentAsString().startsWith("event:unread-count\ndata:{\"count\":3}"),
                result.getResponse().getContentAsString());
    }

    @Test
    void publishReachesSubscriberThroughRedis() throws Exception {
        var streams = streams(RedisTestSupport.template());
        start(new RedisListenerConfig().notificationListenerContainer(RedisTestSupport.connectionFactory(), streams));
        MockMvc mvc = mvc(streams);
        String a = email();
        String b = email();
        MvcResult forA = open(mvc, a, 0);
        MvcResult forB = open(mvc, b, 0);

        streams.publish(a, notification());

        assertTrue(eventually(forA, "\"title\":\"Invoice INV-1 paid\""), forA.getResponse().getContentAsString());
        String content = forA.getResponse().getContentAsString();
        assertTrue(content.contains("event:notification"), content);
        assertTrue(content.contains("id:7"), content);
        Thread.sleep(1000);
        assertFalse(forB.getResponse().getContentAsString().contains("INV-1"));
    }

    @Test
    void publishFallsBackToLocalWhenRedisDown() throws Exception {
        var streams = streams(RedisTestSupport.deadTemplate());
        String a = email();
        MvcResult forA = open(mvc(streams), a, 0);

        assertDoesNotThrow(() -> streams.publish(a, notification()));

        assertTrue(forA.getResponse().getContentAsString().contains("Invoice INV-1 paid"));
    }

    @Test
    void skipsRedisBrieflyAfterAFailure() throws Exception {
        // One markPaid notifies several users; with Redis down each publish would otherwise wait
        // out the Redis timeout in turn.
        var template = mock(StringRedisTemplate.class);
        when(template.convertAndSend(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("down"));
        var streams = streams(template);
        String a = email();
        String b = email();
        MockMvc mvc = mvc(streams);
        MvcResult forA = open(mvc, a, 0);
        MvcResult forB = open(mvc, b, 0);

        streams.publish(a, notification());
        streams.publish(b, notification());

        verify(template, times(1)).convertAndSend(anyString(), anyString());
        assertTrue(forA.getResponse().getContentAsString().contains("Invoice INV-1 paid"));
        assertTrue(forB.getResponse().getContentAsString().contains("Invoice INV-1 paid"));
    }

    @Test
    void listenerContainerStartsWhenRedisDown() {
        var streams = streams(RedisTestSupport.deadTemplate());

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> start(new RedisListenerConfig()
                .notificationListenerContainer(RedisTestSupport.deadConnectionFactory(), streams)));
    }

    private static NotificationStreams streams(StringRedisTemplate template) {
        return new NotificationStreams(template, JsonMapper.builder().build());
    }

    private void start(RedisMessageListenerContainer container) throws Exception {
        containers.add(container);
        container.afterPropertiesSet();
        container.start();
    }

    private static MockMvc mvc(NotificationStreams streams) {
        return MockMvcBuilders.standaloneSetup(new StreamController(streams)).build();
    }

    private static MvcResult open(MockMvc mvc, String email, long unread) throws Exception {
        return mvc.perform(get("/stream").param("email", email).param("unread", String.valueOf(unread))).andReturn();
    }

    private static boolean eventually(MvcResult result, String needle) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (result.getResponse().getContentAsString().contains(needle)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static NotificationResponse notification() {
        return new NotificationResponse(7L, NotificationType.INVOICE_PAID, 9L, "Invoice INV-1 paid", "x",
                false, LocalDateTime.now());
    }

    private static String email() {
        return UUID.randomUUID() + "@t.ph";
    }
}
