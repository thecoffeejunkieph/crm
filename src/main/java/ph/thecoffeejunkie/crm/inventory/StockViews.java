package ph.thecoffeejunkie.crm.inventory;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.catalog.Catalog;
import ph.thecoffeejunkie.crm.catalog.ProductUnit;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read side of stock. Plain SQL: the list joins product for sku, name and unit only (never cost), and the
 * movement refNumber joins whichever document table ref_type points at.
 */
@Service
@Transactional(readOnly = true)
class StockViews {

    private static final BigDecimal ZERO = new BigDecimal("0.000");

    private final JdbcTemplate jdbc;
    private final InventoryPermissions perms;
    private final Users users;
    private final Catalog catalog;
    private final Warehouses warehouses;

    StockViews(JdbcTemplate jdbc, InventoryPermissions perms, Users users, Catalog catalog, Warehouses warehouses) {
        this.jdbc = jdbc;
        this.perms = perms;
        this.users = users;
        this.catalog = catalog;
        this.warehouses = warehouses;
    }

    PageResponse<StockLevelResponse> levels(Long warehouseId, String q, boolean lowOnly, PageQuery page) {
        perms.require(warehouseId, false);
        Pageable pageable = page.toPageable(Set.of(), Sort.unsorted());
        String from = "from stock_level sl join product p on p.id = sl.product_id "
                + "left join (select product_id, warehouse_id, sum(short_qty) short_qty from stock_reservation "
                + "group by product_id, warehouse_id) s on s.product_id = sl.product_id "
                + "and s.warehouse_id = sl.warehouse_id where true";
        List<Object> args = new ArrayList<>();
        if (warehouseId != null) {
            from += " and sl.warehouse_id = ?";
            args.add(warehouseId);
        }
        if (q != null && !q.isBlank()) {
            from += " and (p.sku ilike ? or p.name ilike ?)";
            args.add("%" + q.trim() + "%");
            args.add("%" + q.trim() + "%");
        }
        if (lowOnly) {
            from += " and sl.on_hand = sl.reserved and coalesce(s.short_qty, 0) > 0";
        }
        long total = jdbc.queryForObject("select count(*) " + from, Long.class, args.toArray());
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        List<StockLevelResponse> rows = jdbc.query("select sl.product_id, p.sku, p.name, p.unit, "
                + "p.archived_at is not null archived, sl.warehouse_id, sl.on_hand, sl.reserved, "
                + "sl.on_hand - sl.reserved available, coalesce(s.short_qty, 0)::numeric(12,3) short_qty " + from
                + " order by p.name, p.sku, sl.warehouse_id limit ? offset ?",
                (rs, i) -> new StockLevelResponse(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
                        ProductUnit.valueOf(rs.getString("unit")), rs.getBoolean("archived"),
                        rs.getLong("warehouse_id"), rs.getBigDecimal("on_hand"), rs.getBigDecimal("reserved"),
                        rs.getBigDecimal("available"), rs.getBigDecimal("short_qty")), args.toArray());
        return PageResponse.of(new PageImpl<>(rows, pageable, total));
    }

    PageResponse<MovementResponse> movements(long productId, Long warehouseId, PageQuery page) {
        perms.require(warehouseId, false);
        Pageable pageable = page.toPageable(Set.of(), Sort.unsorted());
        String where = " where m.product_id = ?";
        List<Object> args = new ArrayList<>(List.of(productId));
        if (warehouseId != null) {
            where += " and m.warehouse_id = ?";
            args.add(warehouseId);
        }
        long total = jdbc.queryForObject("select count(*) from stock_movement m" + where, Long.class, args.toArray());
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        List<Object[]> raw = jdbc.query("select m.id, m.type, m.qty, m.ref_type, m.ref_id, "
                + "coalesce(r.number, a.number, t.number, d.number, i.number) ref_number, m.reason, m.created_by, "
                + "m.created_at "
                + "from stock_movement m "
                + "left join stock_receipt r on m.ref_type = 'STOCK_RECEIPT' and r.id = m.ref_id "
                + "left join stock_adjustment a on m.ref_type = 'STOCK_ADJUSTMENT' and a.id = m.ref_id "
                + "left join stock_transfer t on m.ref_type = 'TRANSFER' and t.id = m.ref_id "
                + "left join delivery_order d on m.ref_type = 'DELIVERY_ORDER' and d.id = m.ref_id "
                + "left join invoice i on m.ref_type = 'INVOICE' and i.id = m.ref_id" + where
                + " order by m.id desc limit ? offset ?",
                (rs, i) -> new Object[]{rs.getLong("id"), rs.getString("type"), rs.getBigDecimal("qty"),
                        rs.getString("ref_type"), rs.getLong("ref_id"), rs.getString("ref_number"),
                        rs.getString("reason"), rs.getObject("created_by", Long.class),
                        rs.getTimestamp("created_at")}, args.toArray());
        Map<Long, String> names = users.displayNames(raw.stream().map(o -> (Long) o[7]).filter(Objects::nonNull)
                .distinct().toList());
        List<MovementResponse> rows = raw.stream().map(o -> new MovementResponse((Long) o[0],
                MovementType.valueOf((String) o[1]), (BigDecimal) o[2], (String) o[3], (Long) o[4], (String) o[5],
                (String) o[6], o[7] == null ? null : names.get((Long) o[7]), ((Timestamp) o[8]).toInstant())).toList();
        return PageResponse.of(new PageImpl<>(rows, pageable, total));
    }

    List<AvailabilityResponse> availability(long productId) {
        if (!catalog.info(List.of(productId)).containsKey(productId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Product not found");
        }
        Map<Long, BigDecimal[]> levels = new HashMap<>();
        jdbc.query("select warehouse_id, on_hand, reserved from stock_level where product_id = ?",
                rs -> {
                    levels.put(rs.getLong("warehouse_id"),
                            new BigDecimal[]{rs.getBigDecimal("on_hand"), rs.getBigDecimal("reserved")});
                }, productId);
        return warehouses.listActive().stream().map(w -> {
            BigDecimal[] l = levels.getOrDefault(w.id(), new BigDecimal[]{ZERO, ZERO});
            return new AvailabilityResponse(w.id(), w.name(), l[0], l[1], l[0].subtract(l[1]));
        }).toList();
    }
}
