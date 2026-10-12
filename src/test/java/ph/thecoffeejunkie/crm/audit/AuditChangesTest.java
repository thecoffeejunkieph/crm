package ph.thecoffeejunkie.crm.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuditChangesTest {

    @Test
    void unchangedFieldsSkipped() {
        var c = AuditChanges.diff().field("a", 1, 1).field("b", "x", "y");
        assertThat(c.toMap()).containsOnlyKeys("b");
    }

    @Test
    void bigDecimalScaleIgnored() {
        assertThat(AuditChanges.diff().field("p", new BigDecimal("1.0"), new BigDecimal("1.00")).isEmpty()).isTrue();
    }
}
