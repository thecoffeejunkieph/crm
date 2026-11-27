package ph.thecoffeejunkie.crm.purchasing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionMathTest {

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    private static Optional<BigDecimal> s(String avail, String incoming, String backorder, String level, String target) {
        return SuggestionQuery.suggest(n(avail), n(incoming), n(backorder), n(level), n(target));
    }

    @Test
    void positionExactlyAtLevelIsSuggestedUpToTarget() {
        assertThat(s("8", "0", "0", "8", "20")).contains(n("12"));
    }

    @Test
    void oneAboveLevelIsNotSuggested() {
        assertThat(s("9", "0", "0", "8", "20")).isEmpty();
    }

    @Test
    void backorderLowersPosition() {
        assertThat(s("10", "0", "5", "8", "20")).contains(n("15"));
    }

    @Test
    void incomingCoversIt() {
        assertThat(s("2", "10", "0", "8", "20")).isEmpty();
    }
}
