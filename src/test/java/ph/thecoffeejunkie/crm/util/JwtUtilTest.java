package ph.thecoffeejunkie.crm.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void generatedTokensCarryUniqueJtiAndIssuedAt() {
        var jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "test-secret-key-that-is-long-enough-for-hs256!!");
        var user = new User("a@b.ph", "x", List.of());

        String first = jwtUtil.generateToken(user);
        String second = jwtUtil.generateToken(user);

        assertNotNull(jwtUtil.extractJti(first));
        assertNotEquals(jwtUtil.extractJti(first), jwtUtil.extractJti(second));
        Duration age = Duration.between(jwtUtil.extractIssuedAt(first), Instant.now()).abs();
        assertTrue(age.toSeconds() <= 5, "issuedAt off by " + age);
    }
}
