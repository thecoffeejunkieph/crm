package ph.thecoffeejunkie.crm.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.service.RateLimiter;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterIT {

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter(RedisTestSupport.template()));

    @BeforeEach
    void awayFromWindowEdge() throws InterruptedException {
        // Fixed 60s windows: don't let a burst straddle a boundary and reset mid-test.
        long second = Instant.now().getEpochSecond() % 60;
        if (second > 54) {
            Thread.sleep((61 - second) * 1000);
        }
    }

    @Test
    void blocksEleventhLoginFromSameIpWithin60s() throws Exception {
        String ip = uniqueClient();
        for (int i = 0; i < 10; i++) {
            var chain = failedLogin();
            var res = send(filter, "POST", "/api/v1/auth/login", ip, chain);
            assertEquals(401, res.getStatus());
            assertNotNull(chain.getRequest(), "chain should run for attempt " + (i + 1));
        }

        var chain = failedLogin();
        var res = send(filter, "POST", "/api/v1/auth/login", ip, chain);

        assertEquals(429, res.getStatus());
        int retryAfter = Integer.parseInt(res.getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After was " + retryAfter);
        assertTrue(res.getContentAsString().contains("\"status\":429"));
        assertNull(chain.getRequest(), "chain must not run when limited");
    }

    @Test
    void limitsArePerIp() throws Exception {
        String blocked = uniqueClient();
        for (int i = 0; i < 11; i++) {
            send(filter, "POST", "/api/v1/auth/login", blocked, failedLogin());
        }

        var res = send(filter, "POST", "/api/v1/auth/login", uniqueClient(), new MockFilterChain());

        assertEquals(200, res.getStatus());
    }

    @Test
    void successfulLoginsDoNotCount() throws Exception {
        // A whole office behind one NAT IP logging in each morning must not trip the limit.
        String officeIp = uniqueClient();
        for (int i = 0; i < 25; i++) {
            assertEquals(200, send(filter, "POST", "/api/v1/auth/login", officeIp, new MockFilterChain()).getStatus());
        }
    }

    @Test
    void unlistedPathsAreNeverLimited() throws Exception {
        String ip = uniqueClient();
        for (int i = 0; i < 50; i++) {
            assertEquals(200, send(filter, "GET", "/api/v1/products", ip, new MockFilterChain()).getStatus());
        }
    }

    @Test
    void allowsWhenRedisDown() throws Exception {
        var down = new RateLimitFilter(new RateLimiter(RedisTestSupport.deadTemplate()));
        String ip = uniqueClient();
        for (int i = 0; i < 11; i++) {
            assertEquals(200, send(down, "POST", "/api/v1/auth/login", ip, new MockFilterChain()).getStatus());
        }
    }

    private static MockHttpServletResponse send(RateLimitFilter f, String method, String path, String ip,
                                                MockFilterChain chain) throws Exception {
        var req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        req.setRemoteAddr(ip);
        var res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);
        return res;
    }

    /** A chain that answers like a wrong password does: 401. */
    private static MockFilterChain failedLogin() {
        return new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                res.setStatus(401);
            }
        });
    }

    // Unique per call so re-runs inside the same minute never share a window.
    private static String uniqueClient() {
        return "test-" + java.util.UUID.randomUUID();
    }
}
