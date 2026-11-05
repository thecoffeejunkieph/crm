package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneKeyTest {

    @Test
    void sameMobileWrittenTwoWays() {
        assertThat(PhoneKey.of("+63 917 123 4567")).isEqualTo("09171234567");
        assertThat(PhoneKey.of("0917-123-4567")).isEqualTo("09171234567");
    }

    @Test
    void landlineKeepsItsDigits() {
        assertThat(PhoneKey.of("(02) 8123 4567")).isEqualTo("0281234567");
    }

    @Test
    void tooShortOrBlankIsNoKey() {
        assertThat(PhoneKey.of("12")).isNull();
        assertThat(PhoneKey.of("  ")).isNull();
        assertThat(PhoneKey.of(null)).isNull();
    }
}
