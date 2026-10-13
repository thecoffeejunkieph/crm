package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionSecretsCheckTest {

    private static MockEnvironment profile(String p) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(p);
        return env;
    }

    @Test
    void blankKeyOrSaltFailsOutsideDevAndTest() {
        assertThatThrownBy(() -> new EncryptionSecretsCheck(profile("prod"), "", "salt").check())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EncryptionSecretsCheck(profile("staging"), "key", " ").check())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void setValuesPassAndDevIsExempt() {
        assertThatCode(() -> new EncryptionSecretsCheck(profile("prod"), "key", "salt").check()).doesNotThrowAnyException();
        assertThatCode(() -> new EncryptionSecretsCheck(profile("dev"), "", "").check()).doesNotThrowAnyException();
    }
}
