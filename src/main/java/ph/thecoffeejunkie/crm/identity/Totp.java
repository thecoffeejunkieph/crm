package ph.thecoffeejunkie.crm.identity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s) on the JDK alone. */
final class Totp {

    private static final int PERIOD_SECONDS = 30;

    private Totp() {
    }

    static long stepAt(Instant t) {
        return t.getEpochSecond() / PERIOD_SECONDS;
    }

    /** RFC 4226 section 5.3 dynamic truncation. */
    static int codeAt(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return bin % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The matching step within one step either side of now that is newer than lastUsedStep. */
    static OptionalLong verify(byte[] secret, String code, Instant now, long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = stepAt(now);
        OptionalLong match = OptionalLong.empty();
        for (long step = current - 1; step <= current + 1; step++) {
            byte[] expected = String.format("%06d", codeAt(secret, step)).getBytes(StandardCharsets.US_ASCII);
            // No early exit: every candidate is compared, in constant time.
            if (MessageDigest.isEqual(expected, given) && step > lastUsedStep) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }
}
