package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Returns the response header; records the MDC value seen inside the chain. */
    private String run(String incoming, AtomicReference<String> seenInChain) throws Exception {
        var req = new MockHttpServletRequest();
        if (incoming != null) {
            req.addHeader("X-Request-Id", incoming);
        }
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (rq, rs) -> seenInChain.set(MDC.get("requestId")));
        assertNull(MDC.get("requestId"), "MDC must be cleared after the request");
        return res.getHeader("X-Request-Id");
    }

    @Test
    void validIncomingIdIsEchoed() throws Exception {
        var seen = new AtomicReference<String>();
        assertEquals("abc-123", run("abc-123", seen));
        assertEquals("abc-123", seen.get());
    }

    @Test
    void invalidIncomingIdIsReplacedByUuid() throws Exception {
        var seen = new AtomicReference<String>();
        String id = run("bad id!", seen);
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals(id, seen.get());
    }
}
