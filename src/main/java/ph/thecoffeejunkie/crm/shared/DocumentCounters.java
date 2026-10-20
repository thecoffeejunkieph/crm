package ph.thecoffeejunkie.crm.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Gapless document serials backed by {@code document_counter}. */
@Component
public class DocumentCounters {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    DocumentCounters(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Takes the next serial from the named counter row: the UPDATE locks it until commit, and a rollback rolls the
     * counter back with the document, so numbers are gapless.
     */
    public long next(String name) {
        return jdbc.queryForObject("update document_counter set next_value = next_value + 1 "
                + "where name = ? returning next_value - 1", Long.class, name);
    }

    /** Takes the next serial of {@code counter} and formats it {@code PREFIX-YYYY-00001} (Manila year). */
    public String format(String prefix, String counter) {
        return "%s-%d-%05d".formatted(prefix, LocalDate.now(clock.withZone(MANILA)).getYear(), next(counter));
    }
}
