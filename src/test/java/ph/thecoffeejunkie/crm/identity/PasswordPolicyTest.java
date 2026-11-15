package ph.thecoffeejunkie.crm.identity;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void elevenCharsRejected() {
        assertThatThrownBy(() -> PasswordPolicy.check("ana@tcj.ph", "abcdefghijk"))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.properties()).containsEntry("field", "password");
                });
    }

    @Test
    void equalToEmailIgnoringCaseRejected() {
        assertThatThrownBy(() -> PasswordPolicy.check("someone@example.ph", "SomeOne@Example.PH"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void twelveCharsAccepted() {
        assertThatCode(() -> PasswordPolicy.check("ana@tcj.ph", "abcdefghijkl")).doesNotThrowAnyException();
    }
}
