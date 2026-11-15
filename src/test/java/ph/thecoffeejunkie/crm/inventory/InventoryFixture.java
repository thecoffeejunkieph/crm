package ph.thecoffeejunkie.crm.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds tagged users, customers, warehouses, products, orders and on-hand stock for the inventory ITs.
 * Stock is received through {@link StockLedger}. The movement ledger is append-only for crm_app, so the
 * products and warehouses it references are archived on clean-up rather than deleted.
 */
public final class InventoryFixture {

    public record Line(long productId, String qty) {}

    public final String tag = "IF" + UUID.randomUUID().toString().substring(0, 8);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final StockLedger ledger;
    private final long customer;
    private int orders;

    InventoryFixture(JdbcTemplate jdbc, TransactionTemplate tx, StockLedger ledger) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.ledger = ledger;
        customer = jdbc.queryForObject("insert into customer (type, display_name, tin, address, payment_terms) "
                + "values ('BUSINESS', ?, '111-222-333', 'Makati', 'NET_30') returning id", Long.class, tag);
    }

    /** For ITs outside this package, which cannot name {@link StockLedger}. */
    public static InventoryFixture create(JdbcTemplate jdbc, TransactionTemplate tx,
                                          org.springframework.context.ApplicationContext ctx) {
        return new InventoryFixture(jdbc, tx, ctx.getBean(StockLedger.class));
    }

    /** A staff session for MockMvc; warehouseIds only matter for WAREHOUSE. */
    public static RequestPostProcessor as(long userId, Role role, Long... warehouseIds) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(userId,
                        "u" + userId + "@if.it", "Staff " + userId, role, Set.of(warehouseIds)), null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    public void archive(long productId) {
        jdbc.update("update product set archived_at = now() where id = ?", productId);
    }

    public static Line l(long productId, String qty) {
        return new Line(productId, qty);
    }

    public long user(String name) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', 'SALES') returning id", Long.class, tag + name + "@if.it", name);
    }

    public long admin(String name) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', 'ADMIN') returning id", Long.class, tag + name + "@if.it", name);
    }

    /** A WAREHOUSE user assigned to the warehouse; withRole lets a test give an admin a membership too. */
    public long warehouseUser(String name, long warehouseId) {
        long id = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', 'WAREHOUSE') returning id", Long.class, tag + name + "@if.it", name);
        member(id, warehouseId);
        return id;
    }

    public void member(long userId, long warehouseId) {
        jdbc.update("insert into user_warehouse (user_id, warehouse_id) values (?, ?)", userId, warehouseId);
    }

    public void reorderLevel(long productId, String level) {
        jdbc.update("update product set reorder_level = ?::numeric where id = ?", level, productId);
    }

    /** "TYPE|entity_type|entity_id" per notification, oldest first. */
    public List<String> notifications(long userId) {
        return jdbc.queryForList("select type || '|' || entity_type || '|' || entity_id from notification "
                + "where recipient_user_id = ? order by id", String.class, userId);
    }

    public long warehouse(String code) {
        return jdbc.queryForObject("insert into warehouse (code, name) values (?, 'Fixture') returning id",
                Long.class, tag + code);
    }

    public long product(String sku, String unit) {
        return jdbc.queryForObject("insert into product (sku, name, unit, price, tax_class) "
                + "values (?, ?, ?, 10, 'VATABLE') returning id", Long.class, tag + sku, "Product " + sku, unit);
    }

    public void receive(long productId, long warehouseId, String qty) {
        receive(productId, warehouseId, qty, null);
    }

    public void receive(long productId, long warehouseId, String qty, Long linkedOrderId) {
        tx.executeWithoutResult(s -> ledger.post(MovementType.RECEIPT, productId, warehouseId, new BigDecimal(qty),
                "TEST", 0, null, linkedOrderId));
    }

    /** A CONFIRMED order with real order lines; the returned stock request lists them in insertion order. */
    public OrderStock order(long repId, long warehouseId, Instant confirmedAt, Line... lines) {
        String number = tag + "-" + (++orders);
        long id = jdbc.queryForObject("insert into sales_order (number, customer_id, rep_id, status, payment_terms, "
                + "confirmed_at, created_by, ship_from_warehouse_id) values (?, ?, ?, 'CONFIRMED', 'NET_30', ?, ?, ?) "
                + "returning id", Long.class, number, customer, repId, Timestamp.from(confirmedAt), repId, warehouseId);
        List<ReserveLine> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Line l = lines[i];
            long lineId = jdbc.queryForObject("insert into order_line (order_id, position, product_id, description, "
                    + "unit, qty, unit_price, tax_class, line_total) values (?, ?, ?, 'x', 'PC', ?::numeric, 0, "
                    + "'VATABLE', 0) returning id", Long.class, id, i, l.productId(), l.qty());
            out.add(new ReserveLine(lineId, l.productId(), new BigDecimal(l.qty())));
        }
        return new OrderStock(id, number, repId, warehouseId, confirmedAt, out);
    }

    public BigDecimal onHand(long productId, long warehouseId) {
        return level(productId, warehouseId, "on_hand");
    }

    public BigDecimal reserved(long productId, long warehouseId) {
        return level(productId, warehouseId, "reserved");
    }

    private BigDecimal level(long productId, long warehouseId, String column) {
        return jdbc.queryForObject("select coalesce((select " + column + " from stock_level where product_id = ? "
                + "and warehouse_id = ?), 0)", BigDecimal.class, productId, warehouseId);
    }

    public List<String> notificationTitles(long userId) {
        return jdbc.queryForList("select title from notification where recipient_user_id = ? "
                + "and type = 'ORDER_RESERVED' order by id", String.class, userId);
    }

    public void cleanUp() {
        String orderIds = "select id from sales_order where customer_id = " + customer;
        String products = "select id from product where sku like '" + tag + "%'";
        String warehouses = "select id from warehouse where code like '" + tag + "%'";
        jdbc.update("delete from stock_receipt where warehouse_id in (" + warehouses + ")");
        jdbc.update("delete from stock_reservation where sales_order_id in (" + orderIds + ")");
        jdbc.update("delete from stock_level where product_id in (" + products + ")");
        jdbc.update("delete from notification where recipient_user_id in (select id from app_user where email like ?)",
                tag + "%");
        jdbc.update("delete from order_line where order_id in (" + orderIds + ")");
        jdbc.update("delete from sales_order where customer_id = ?", customer);
        jdbc.update("delete from customer where id = ?", customer);
        jdbc.update("delete from user_warehouse where user_id in (select id from app_user where email like ?)", tag + "%");
        // The append-only ledger may reference the user (created_by): archive those instead of deleting them.
        jdbc.update("update app_user set archived_at = now() where email like ? "
                + "and id in (select created_by from stock_movement where created_by is not null)", tag + "%");
        jdbc.update("delete from app_user where email like ? and archived_at is null", tag + "%");
        jdbc.update("update product set archived_at = now() where sku like ?", tag + "%");
        jdbc.update("update warehouse set archived_at = now() where code like ?", tag + "%");
    }
}
