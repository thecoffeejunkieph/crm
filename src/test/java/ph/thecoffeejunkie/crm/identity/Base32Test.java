package ph.thecoffeejunkie.crm.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class Base32Test {

    @Test
    void rfc4648Vector() {
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Base32.decode("mzxw 6ytb oi")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void roundTripsRandomSecret() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret);
    }
}
