package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.Test;
import ph.thecoffeejunkie.crm.shared.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDateTest {

    // 23:30 UTC on Oct 19 is 07:30 on Oct 20 in Manila; the JVM runs on UTC in production.
    private static final Clock UTC_EVENING = Clock.fixed(Instant.parse("2026-10-19T23:30:00Z"), ZoneOffset.UTC);

    @Test
    void manilaTodayIsAcceptedWhileUtcIsStillYesterday() {
        assertThatCode(() -> PaymentService.requireNotFuture(LocalDate.of(2026, 10, 20), UTC_EVENING))
                .doesNotThrowAnyException();
    }

    @Test
    void manilaTomorrowIsRefused() {
        assertThatThrownBy(() -> PaymentService.requireNotFuture(LocalDate.of(2026, 10, 21), UTC_EVENING))
                .isInstanceOf(DomainException.class);
    }
}
