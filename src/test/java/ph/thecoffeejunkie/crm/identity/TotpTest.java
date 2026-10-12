package ph.thecoffeejunkie.crm.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rfc6238Vectors() {
        // RFC 6238 Appendix B SHA-1 values, truncated to 6 digits.
        assertThat(Totp.codeAt(SECRET, Totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo(287082);
        assertThat(Totp.codeAt(SECRET, Totp.stepAt(Instant.ofEpochSecond(1111111109L)))).isEqualTo(81804);
        assertThat(Totp.codeAt(SECRET, Totp.stepAt(Instant.ofEpochSecond(1234567890L)))).isEqualTo(5924);
        assertThat(Totp.codeAt(SECRET, Totp.stepAt(Instant.ofEpochSecond(2000000000L)))).isEqualTo(279037);
    }

    @Test
    void currentStepAccepted() {
        assertThat(Totp.verify(SECRET, "287082", Instant.ofEpochSecond(59), -1).getAsLong()).isEqualTo(1);
    }

    @Test
    void previousStepAccepted() {
        // 287082 is step 1; "now" is in step 2.
        assertThat(Totp.verify(SECRET, "287082", Instant.ofEpochSecond(60), -1).getAsLong()).isEqualTo(1);
    }

    @Test
    void twoStepsAgoRejected() {
        assertThat(Totp.verify(SECRET, "287082", Instant.ofEpochSecond(90), -1)).isEmpty();
    }

    @Test
    void replaySameStepRejected() {
        assertThat(Totp.verify(SECRET, "287082", Instant.ofEpochSecond(59), 1)).isEmpty();
    }

    @Test
    void leadingZerosAndGarbage() {
        assertThat(Totp.verify(SECRET, "081804", Instant.ofEpochSecond(1111111109L), -1)).isPresent();
        assertThat(Totp.verify(SECRET, "81804", Instant.ofEpochSecond(1111111109L), -1)).isEmpty();
        assertThat(Totp.verify(SECRET, "abcdef", Instant.ofEpochSecond(59), -1)).isEmpty();
    }
}
