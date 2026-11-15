package ph.thecoffeejunkie.crm.inventory;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.inventory.InventoryOverview.Health;
import ph.thecoffeejunkie.crm.inventory.InventoryOverview.ProductStock;
import ph.thecoffeejunkie.crm.inventory.InventoryOverview.WarehouseValue;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Read-only stock health for the Inventory dashboard. All active products in one response. */
@Service
@Transactional(readOnly = true)
class InventoryOverviewService {

    static final int SLOW_DAYS = 90;
    private static final BigDecimal ZERO = new BigDecimal("0.000");

    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryPermissions perms;
    private final CurrentActor actor;
    private final Clock clock;

    InventoryOverviewService(NamedParameterJdbcTemplate jdbc, InventoryPermissions perms, CurrentActor actor, Clock clock) {
        this.jdbc = jdbc;
        this.perms = perms;
        this.actor = actor;
        this.clock = clock;
    }

    /** {@code warehouseId} null: every warehouse for ADMIN/SALES, the assigned ones for WAREHOUSE. */
    InventoryOverview overview(Long warehouseId) {
        Scope scope = actor.scope();
        Set<Long> warehouses;
        if (warehouseId != null) {
            perms.require(warehouseId, false);
            warehouses = Set.of(warehouseId);
        } else {
            warehouses = scope.role() == Role.WAREHOUSE ? scope.warehouseIds() : null;
        }
        boolean admin = scope.role() == Role.ADMIN;
        Instant now = clock.instant();
        MapSqlParameterSource p = new MapSqlParameterSource("since", Timestamp.from(now.minus(Duration.ofDays(30))))
                .addValue("wh", warehouses == null || warehouses.isEmpty() ? List.of(-1L) : warehouses)
                .addValue("all", warehouses == null);
        String inScope = " and (:all or %s.warehouse_id in (:wh))";
        // ponytail: every active product in one go; page it if the catalog grows past a few thousand.
        List<ProductStock> rows = jdbc.query("with lvl as (select sl.product_id, sum(sl.on_hand) on_hand, sum(sl.reserved) reserved, "
                + "count(*) levels from stock_level sl where true" + inScope.formatted("sl") + " group by sl.product_id), "
                + "disp as (select m.product_id, -sum(m.qty) filter (where m.created_at >= :since) shipped, "
                + "max(m.created_at) last_at from stock_movement m where m.type = 'DISPATCH'" + inScope.formatted("m")
                + " group by m.product_id) "
                + "select p.id, p.sku, p.name, p.unit, p.cost, p.reorder_level, coalesce(l.on_hand, 0) on_hand, "
                + "coalesce(l.reserved, 0) reserved, coalesce(l.levels, 0) levels, coalesce(o.shipped, 0) shipped, o.last_at "
                + "from product p left join lvl l on l.product_id = p.id left join disp o on o.product_id = p.id "
                + "where p.archived_at is null order by p.name, p.sku", p, (rs, i) -> {
                    BigDecimal onHand = rs.getBigDecimal("on_hand").setScale(3);
                    BigDecimal reserved = rs.getBigDecimal("reserved").setScale(3);
                    BigDecimal available = onHand.subtract(reserved);
                    BigDecimal level = rs.getBigDecimal("reorder_level");
                    BigDecimal shipped = rs.getBigDecimal("shipped").setScale(3);
                    Timestamp last = rs.getTimestamp("last_at");
                    Instant lastAt = last == null ? null : last.toInstant();
                    // Never stocked and no threshold: not a stocked item (yet), so not "out".
                    boolean tracked = rs.getLong("levels") > 0 || level != null;
                    Health health = !tracked ? Health.OK
                            : available.signum() <= 0 ? Health.OUT
                            : level != null && available.compareTo(level) <= 0 ? Health.LOW : Health.OK;
                    Integer cover = shipped.signum() > 0 && available.signum() > 0
                            ? available.multiply(BigDecimal.valueOf(30)).divide(shipped, 0, RoundingMode.DOWN).intValue() : null;
                    boolean slow = onHand.signum() > 0 && (lastAt == null || lastAt.isBefore(now.minus(Duration.ofDays(SLOW_DAYS))));
                    BigDecimal cost = admin ? rs.getBigDecimal("cost") : null;
                    BigDecimal value = cost == null ? null : cost.multiply(onHand).setScale(2, RoundingMode.HALF_UP);
                    return new ProductStock(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            ProductUnit.valueOf(rs.getString("unit")), onHand, reserved, available, level, health,
                            shipped, cover, lastAt, slow, cost, value);
                });

        BigDecimal total = null;
        Long withoutCost = null;
        List<WarehouseValue> byWarehouse = null;
        if (admin) {
            total = rows.stream().map(ProductStock::value).filter(v -> v != null).reduce(new BigDecimal("0.00"), BigDecimal::add);
            withoutCost = rows.stream().filter(r -> r.onHand().signum() > 0 && r.unitCost() == null).count();
            byWarehouse = jdbc.query("select w.id, w.name, coalesce(sum(sl.on_hand * p.cost), 0) val from warehouse w "
                    + "left join stock_level sl on sl.warehouse_id = w.id left join product p on p.id = sl.product_id "
                    + "and p.archived_at is null where w.archived_at is null and (:all or w.id in (:wh)) group by w.id, w.name order by val desc, w.name",
                    p, (rs, i) -> new WarehouseValue(rs.getLong("id"), rs.getString("name"),
                            rs.getBigDecimal("val").setScale(2, RoundingMode.HALF_UP)));
        }
        return new InventoryOverview(total, withoutCost,
                rows.stream().filter(r -> r.onHand().signum() > 0).count(),
                rows.stream().filter(r -> r.health() == Health.LOW).count(),
                rows.stream().filter(r -> r.health() == Health.OUT).count(),
                rows.stream().filter(ProductStock::slow).count(), byWarehouse, rows);
    }
}
