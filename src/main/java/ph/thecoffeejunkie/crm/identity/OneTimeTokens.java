package ph.thecoffeejunkie.crm.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Single-use tokens for links sent by email. Only the SHA-256 of a token is stored (the raw value is high-entropy,
 * so a fast hash is enough), and redeeming is one conditional UPDATE so concurrent redeems cannot both win.
 * Both methods join the caller's transaction: a redeem rolls back with a failed follow-up write.
 */
@Service
public class OneTimeTokens {

    public record Subject(String type, long id) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    OneTimeTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the raw token; earlier unused tokens of the same purpose and subject stop working. */
    @Transactional
    public String issue(TokenPurpose purpose, String subjectType, long subjectId, Duration ttl, Long createdBy) {
        jdbc.update("UPDATE one_time_token SET used_at = now() "
                + "WHERE purpose = ? AND subject_type = ? AND subject_id = ? AND used_at IS NULL",
                purpose.name(), subjectType, subjectId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO one_time_token (purpose, subject_type, subject_id, token_hash, expires_at, created_by) "
                + "VALUES (?, ?, ?, ?, now() + make_interval(secs => ?), ?)",
                purpose.name(), subjectType, subjectId, hash(raw), ttl.toMillis() / 1000.0, createdBy);
        return raw;
    }

    /** Empty when the token is unknown, expired, for another purpose, or already used. */
    @Transactional
    public Optional<Subject> redeem(TokenPurpose purpose, String rawToken) {
        if (rawToken == null) {
            return Optional.empty();
        }
        List<Subject> hit = jdbc.query("UPDATE one_time_token SET used_at = now() "
                        + "WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > now() "
                        + "RETURNING subject_type, subject_id",
                (rs, i) -> new Subject(rs.getString(1), rs.getLong(2)), hash(rawToken), purpose.name());
        return hit.stream().findFirst();
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
