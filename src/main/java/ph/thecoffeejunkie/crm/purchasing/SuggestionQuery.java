package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.purchasing.SuggestionResponse.SuggestionGroup;
import ph.thecoffeejunkie.crm.purchasing.SuggestionResponse.SuggestionItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Products at or below their reorder level, grouped by preferred supplier. Computed on read, one SQL. */
@Component
@Transactional(readOnly = true)
class SuggestionQuery {

    private static final int VELOCITY_DAYS = 30;
    private static final String OPEN_PO = "o.status in ('PENDING_APPROVAL','SENT','PARTIALLY_RECEIVED') and not o.closed_short";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    SuggestionQuery(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** position = available + incoming - backorder; suggested when position <= level, quantity target - position. */
    static Optional<BigDecimal> suggest(BigDecimal available, BigDecimal incoming, BigDecimal backorder,
                                        BigDecimal level, BigDecimal target) {
        BigDecimal position = available.add(incoming).subtract(backorder);
        if (position.compareTo(level) > 0) {
            return Optional.empty();
        }
        BigDecimal qty = target.subtract(position);
        return qty.signum() > 0 ? Optional.of(qty) : Optional.empty();
    }

    SuggestionResponse compute() {
        var since = new MapSqlParameterSource("since",
                Timestamp.from(clock.instant().minus(Duration.ofDays(VELOCITY_DAYS))));
        // DISPATCH movements are negative, hence abs(). Only products with both level and target reach suggest().
        List<Row> rows = jdbc.query("select p.id, p.sku, p.name, p.unit, p.reorder_level, p.reorder_target, "
                + "s.id supplier_id, s.name supplier_name, s.lead_time_days, "
                + "coalesce((select sum(sl.on_hand - sl.reserved) from stock_level sl where sl.product_id = p.id), 0) available, "
                + "coalesce((select sum(r.short_qty) from stock_reservation r where r.product_id = p.id), 0) backorder, "
                + "coalesce((select sum(l.qty - l.received_qty) from purchase_order_line l join purchase_order o on o.id = l.po_id "
                + "where l.product_id = p.id and " + OPEN_PO + "), 0) incoming, "
                + "coalesce((select abs(sum(m.qty)) from stock_movement m where m.product_id = p.id "
                + "and m.type = 'DISPATCH' and m.created_at >= :since), 0) shipped, "
                + "(select o.number from purchase_order_line l join purchase_order o on o.id = l.po_id "
                + "where l.product_id = p.id and o.status = 'DRAFT' order by o.id desc limit 1) draft_po "
                + "from product p left join supplier s on s.id = p.preferred_supplier_id "
                + "where p.archived_at is null and p.reorder_level is not null and p.reorder_target is not null "
                + "order by p.name, p.sku", since, (rs, i) -> {
                    BigDecimal available = rs.getBigDecimal("available").setScale(3);
                    BigDecimal backorder = rs.getBigDecimal("backorder").setScale(3);
                    BigDecimal incoming = rs.getBigDecimal("incoming").setScale(3);
                    BigDecimal level = rs.getBigDecimal("reorder_level");
                    BigDecimal target = rs.getBigDecimal("reorder_target");
                    Optional<BigDecimal> qty = suggest(available, incoming, backorder, level, target);
                    if (qty.isEmpty()) {
                        return null;
                    }
                    BigDecimal perDay = rs.getBigDecimal("shipped").divide(BigDecimal.valueOf(VELOCITY_DAYS), 3, RoundingMode.HALF_UP);
                    BigDecimal cover = perDay.signum() == 0 ? null : available.divide(perDay, 1, RoundingMode.HALF_UP);
                    long supplierId = rs.getLong("supplier_id");
                    boolean noSupplier = rs.wasNull();
                    int lead = rs.getInt("lead_time_days");
                    Integer leadDays = rs.wasNull() ? null : lead;
                    return new Row(noSupplier ? null : supplierId, rs.getString("supplier_name"), leadDays,
                            new SuggestionItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getString("unit"),
                                    available, backorder, incoming, level, target, qty.get(), perDay, cover, rs.getString("draft_po")));
                }).stream().filter(Objects::nonNull).toList();

        Map<Long, List<Row>> bySupplier = new LinkedHashMap<>();
        for (Row r : rows) {
            bySupplier.computeIfAbsent(r.supplierId, k -> new ArrayList<>()).add(r);
        }
        List<SuggestionGroup> groups = bySupplier.values().stream()
                .map(g -> new SuggestionGroup(g.get(0).supplierId, g.get(0).supplierName, g.get(0).leadDays,
                        g.stream().map(Row::item).toList()))
                .sorted(Comparator.comparing((SuggestionGroup g) -> g.supplierId() == null)
                        .thenComparing(g -> g.supplierName() == null ? "" : g.supplierName()))
                .toList();
        return new SuggestionResponse(groups);
    }

    private record Row(Long supplierId, String supplierName, Integer leadDays, SuggestionItem item) {
    }
}
