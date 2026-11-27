package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RestockRhythmTest {

    // fallback 15, rhythm 80%, min 3
    static final RuleSettings S = new RuleSettings(Set.of(), 15, 80, 3, 60, 3, 2, 3, 14, null);
    static final LocalDate NEWEST = LocalDate.of(2026, 11, 1);

    /** Dates newest first, each {@code gaps[i]} days before the previous one. */
    static List<LocalDate> dates(int... gaps) {
        List<LocalDate> out = new ArrayList<>(List.of(NEWEST));
        for (int g : gaps) {
            out.add(out.getLast().minusDays(g));
        }
        return out;
    }

    @Test
    void fewerThanTwoDeliveriesUsesFallback() {
        assertThat(RestockRhythm.dueAfterDays(List.of(), S)).isEqualTo(15);
        assertThat(RestockRhythm.dueAfterDays(dates(), S)).isEqualTo(15);
    }

    @Test
    void averageGapTimesPercent() {
        assertThat(RestockRhythm.dueAfterDays(dates(20, 20, 20), S)).isEqualTo(16);
    }

    @Test
    void roundsHalfUp() {
        assertThat(RestockRhythm.dueAfterDays(dates(10, 11), S)).isEqualTo(8); // 10.5 x 0.8 = 8.4
        assertThat(RestockRhythm.dueAfterDays(dates(13, 12), S)).isEqualTo(10); // 12.5 x 0.8 = 10.0
        assertThat(RestockRhythm.dueAfterDays(dates(5, 5, 5, 5, 5, 5, 5, 6), new RuleSettings(Set.of(), 15, 50, 1, 60,
                3, 2, 3, 14, null))).isEqualTo(3); // 5 x 0.5 = 2.5 rounds up, not down
    }

    @Test
    void onlyFiveNewestGapsCount() {
        // Seven dates: the five newest gaps are 10, the oldest is 100 and must be ignored.
        assertThat(RestockRhythm.dueAfterDays(dates(10, 10, 10, 10, 10, 100), S)).isEqualTo(8);
    }

    @Test
    void sameDayDeliveriesFloorAtMinDays() {
        assertThat(RestockRhythm.dueAfterDays(dates(0, 0), S)).isEqualTo(3);
    }
}
