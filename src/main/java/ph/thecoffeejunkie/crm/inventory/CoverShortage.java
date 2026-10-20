package ph.thecoffeejunkie.crm.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure planner for covering an order's shortage from other warehouses. */
final class CoverShortage {

    private CoverShortage() {
    }

    /**
     * Source warehouse to the lines it should send (orderLineId is 0: a plan line is per product). Per product, one
     * warehouse covers it alone if the one with most stock can; otherwise stock is taken largest first until covered
     * or exhausted. shipFrom is never a source; ties go to the lower warehouse id.
     */
    static Map<Long, List<ReserveLine>> plan(Map<Long, BigDecimal> shortByProduct,
                                             Map<Long, Map<Long, BigDecimal>> availableByProductByWarehouse,
                                             long shipFrom) {
        Map<Long, List<ReserveLine>> out = new TreeMap<>();
        new TreeMap<>(shortByProduct).forEach((productId, shortQty) -> {
            List<Map.Entry<Long, BigDecimal>> sources = new ArrayList<>(
                    availableByProductByWarehouse.getOrDefault(productId, Map.of()).entrySet().stream()
                            .filter(e -> e.getKey() != shipFrom && e.getValue().signum() > 0).toList());
            sources.sort(Map.Entry.<Long, BigDecimal>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()));
            BigDecimal left = shortQty;
            for (var s : sources) {
                if (left.signum() <= 0) {
                    break;
                }
                BigDecimal take = left.min(s.getValue());
                out.computeIfAbsent(s.getKey(), k -> new ArrayList<>()).add(new ReserveLine(0, productId, take));
                left = left.subtract(take);
            }
        });
        return out;
    }
}
