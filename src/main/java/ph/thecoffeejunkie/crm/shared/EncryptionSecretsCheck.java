package ph.thecoffeejunkie.crm.shared;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Compose turns an unset variable into "" and Spring resolves "${ENCRYPTION_KEY}" to it, so a missing secret would
 * otherwise start the app with a key derived from the empty string. Refuse that outside dev and test.
 */
@Component
class EncryptionSecretsCheck {

    private final Environment env;
    private final String key;
    private final String salt;

    EncryptionSecretsCheck(Environment env,
                           @Value("${app.security.encryption-key:}") String key,
                           @Value("${encryption.salt:}") String salt) {
        this.env = env;
        this.key = key;
        this.salt = salt;
    }

    @PostConstruct
    void check() {
        if (env.acceptsProfiles(Profiles.of("dev", "test"))) {
            return;
        }
        if (key.isBlank() || salt.isBlank()) {
            throw new IllegalStateException("ENCRYPTION_KEY and ENCRYPTION_SALT must be set to non-blank values");
        }
    }
}
