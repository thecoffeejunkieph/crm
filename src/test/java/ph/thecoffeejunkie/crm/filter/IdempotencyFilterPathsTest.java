package ph.thecoffeejunkie.crm.filter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class IdempotencyFilterPathsTest {

    @Test
    void posCheckoutIsIdempotent() {
        IdempotencyFilter filter = new IdempotencyFilter(mock(StringRedisTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pos/sales");
        request.addHeader("Idempotency-Key", "k1");

        assertFalse(filter.shouldNotFilter(request));
    }
}
