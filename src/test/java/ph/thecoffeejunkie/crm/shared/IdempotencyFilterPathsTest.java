package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class IdempotencyFilterPathsTest {

    @Test
    void posCheckoutIsIdempotent() {
        IdempotencyFilter filter = new IdempotencyFilter(mock(StringRedisTemplate.class), List.of("/api/v1/pos/sales"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pos/sales");
        request.addHeader("Idempotency-Key", "k1");

        assertFalse(filter.shouldNotFilter(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/customers", "/api/customers/5/contacts", "/api/approvals", "/api/admin/users",
            "/api/warehouses", "/api/orders/5/invoice", "/api/orders/5/invoice/reissue",
            "/api/orders/5/payments"})
    void createEndpointsAreIdempotentByDefault(String path) {
        IdempotencyFilter filter = new IdempotencyFilter(mock(StringRedisTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Idempotency-Key", "k1");

        assertFalse(filter.shouldNotFilter(request), path);
    }
}
