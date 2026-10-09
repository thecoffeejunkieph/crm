package ph.thecoffeejunkie.crm.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.RedisTestSupport;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class IdempotencyFilterIT {

    @RestController
    static class CountingController {
        final AtomicInteger count = new AtomicInteger();

        @PostMapping("/api/v1/quotations")
        ResponseEntity<Map<String, Integer>> create(@RequestParam(defaultValue = "false") boolean fail) {
            int n = count.incrementAndGet();
            return fail ? ResponseEntity.status(500).body(Map.of("n", n)) : ResponseEntity.ok(Map.of("n", n));
        }

        @PostMapping("/api/v1/customers")
        Map<String, Integer> customers() {
            return Map.of("n", count.incrementAndGet());
        }
    }

    private final CountingController controller = new CountingController();
    private final MockMvc mvc = mvc(new IdempotencyFilter(RedisTestSupport.template()));

    @AfterEach
    void clearUser() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameKeyRunsOnceAndReplays() throws Exception {
        String key = key();

        MvcResult first = mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key)).andReturn();
        MvcResult second = mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key)).andReturn();

        assertEquals(1, controller.count.get());
        assertEquals("{\"n\":1}", first.getResponse().getContentAsString());
        assertEquals("{\"n\":1}", second.getResponse().getContentAsString());
        assertEquals(200, second.getResponse().getStatus());
        assertNull(first.getResponse().getHeader("Idempotent-Replayed"));
        assertEquals("true", second.getResponse().getHeader("Idempotent-Replayed"));
    }

    @Test
    void noHeaderIsNotDeduplicated() throws Exception {
        mvc.perform(post("/api/v1/quotations"));
        mvc.perform(post("/api/v1/quotations"));

        assertEquals(2, controller.count.get());
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String key = key();

        login("user1@b.ph");
        mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key));
        login("user2@b.ph");
        mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key));

        assertEquals(2, controller.count.get());
    }

    @Test
    void releasesKeyOnFailure() throws Exception {
        String key = key();

        int failed = mvc.perform(post("/api/v1/quotations?fail=true").header("Idempotency-Key", key))
                .andReturn().getResponse().getStatus();
        int retried = mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key))
                .andReturn().getResponse().getStatus();

        assertEquals(500, failed);
        assertEquals(200, retried);
        assertEquals(2, controller.count.get());
    }

    @Test
    void inProgressKeyReturns409() throws Exception {
        String key = key();
        RedisTestSupport.template().opsForValue()
                .set("idem:anonymous:POST:/api/v1/quotations:" + key, "IN_PROGRESS");

        int status = mvc.perform(post("/api/v1/quotations").header("Idempotency-Key", key))
                .andReturn().getResponse().getStatus();

        assertEquals(409, status);
        assertEquals(0, controller.count.get());
    }

    @Test
    void passesThroughWhenRedisDown() throws Exception {
        MockMvc down = mvc(new IdempotencyFilter(RedisTestSupport.deadTemplate()));

        int status = down.perform(post("/api/v1/quotations").header("Idempotency-Key", key()))
                .andReturn().getResponse().getStatus();

        assertEquals(200, status);
        assertEquals(1, controller.count.get());
    }

    @Test
    void unlistedPathsIgnored() throws Exception {
        String key = key();

        mvc.perform(post("/api/v1/customers").header("Idempotency-Key", key));
        mvc.perform(post("/api/v1/customers").header("Idempotency-Key", key));

        assertEquals(2, controller.count.get());
    }

    private MockMvc mvc(IdempotencyFilter filter) {
        return MockMvcBuilders.standaloneSetup(controller).addFilters(filter).build();
    }

    private static void login(String user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
