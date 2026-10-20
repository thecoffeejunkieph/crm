package ph.thecoffeejunkie.crm.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoverShortageTest {

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    private static Map<Long, BigDecimal> av(Object... kv) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Long) kv[i], n((String) kv[i + 1]));
        }
        return m;
    }

    private static List<String> flat(Map<Long, List<ReserveLine>> plan) {
        return plan.entrySet().stream().flatMap(e -> e.getValue().stream()
                .map(l -> e.getKey() + ":" + l.productId() + "=" + l.qty().toPlainString())).toList();
    }

    @Test
    void singleSourcePreferred() {
        var plan = CoverShortage.plan(Map.of(1L, n("10")), Map.of(1L, av(2L, "4", 3L, "12", 4L, "30")), 9L);
        assertThat(flat(plan)).containsExactly("4:1=10");
    }

    @Test
    void splitsOnlyWhenNoSingleCovers() {
        var plan = CoverShortage.plan(Map.of(1L, n("10")), Map.of(1L, av(2L, "4", 3L, "7")), 9L);
        assertThat(flat(plan)).containsExactlyInAnyOrder("3:1=7", "2:1=3");
    }

    @Test
    void neverShipFrom() {
        var plan = CoverShortage.plan(Map.of(1L, n("5")), Map.of(1L, av(9L, "100", 2L, "6")), 9L);
        assertThat(flat(plan)).containsExactly("2:1=5");
    }

    @Test
    void tieBreaksByLowerId() {
        var plan = CoverShortage.plan(Map.of(1L, n("5")), Map.of(1L, av(3L, "8", 2L, "8")), 9L);
        assertThat(flat(plan)).containsExactly("2:1=5");
        var split = CoverShortage.plan(Map.of(1L, n("10")), Map.of(1L, av(3L, "6", 2L, "6")), 9L);
        assertThat(flat(split)).containsExactlyInAnyOrder("2:1=6", "3:1=4");
    }

    @Test
    void partialWhenNothingCovers() {
        var plan = CoverShortage.plan(Map.of(1L, n("10")), Map.of(1L, av(2L, "3", 3L, "2", 4L, "0")), 9L);
        assertThat(flat(plan)).containsExactlyInAnyOrder("2:1=3", "3:1=2");
        assertThat(CoverShortage.plan(Map.of(1L, n("10")), Map.of(), 9L)).isEmpty();
    }
}
