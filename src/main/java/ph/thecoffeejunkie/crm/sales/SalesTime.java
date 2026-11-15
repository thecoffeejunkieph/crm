package ph.thecoffeejunkie.crm.sales;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business dates (quote validity, due dates, overdue) are Manila dates. */
final class SalesTime {

    static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private SalesTime() {
    }

    static LocalDate today(Clock c) {
        return LocalDate.now(c.withZone(MANILA));
    }
}
