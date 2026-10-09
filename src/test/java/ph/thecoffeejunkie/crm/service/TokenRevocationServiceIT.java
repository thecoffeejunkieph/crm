package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import ph.thecoffeejunkie.crm.RedisTestSupport;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRevocationServiceIT {

    private final StringRedisTemplate template = RedisTestSupport.template();
    private final TokenRevocationService service = new TokenRevocationService(template);

    @Test
    void revokedJtiIsRevokedUntilExpiry() {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();

        service.revoke(jti, now.plusSeconds(60));

        assertTrue(service.isRevoked(jti, "a@b.ph", now));
        assertFalse(service.isRevoked(UUID.randomUUID().toString(), "a@b.ph", now));
        long ttl = template.getExpire("auth:deny:" + jti);
        assertTrue(ttl >= 1 && ttl <= 60, "ttl was " + ttl);
    }

    @Test
    void revokeAllForRevokesTokensIssuedBeforeOnly() {
        String user = UUID.randomUUID() + "@b.ph";
        Instant now = Instant.now();

        service.revokeAllFor(user);

        assertTrue(service.isRevoked("old", user, now.minusSeconds(5)));
        assertFalse(service.isRevoked("new", user, now.plusSeconds(2)));
        assertFalse(service.isRevoked("other", "someone-else@b.ph", now.minusSeconds(5)));
    }

    @Test
    void alreadyExpiredTokenIsNotStored() {
        String jti = UUID.randomUUID().toString();

        service.revoke(jti, Instant.now().minusSeconds(1));

        assertFalse(template.hasKey("auth:deny:" + jti));
    }

    @Test
    void isRevoked_failsClosedWhenRedisDown() {
        var down = new TokenRevocationService(RedisTestSupport.deadTemplate());

        assertTrue(down.isRevoked("any", "a@b.ph", Instant.now()));
    }
}
