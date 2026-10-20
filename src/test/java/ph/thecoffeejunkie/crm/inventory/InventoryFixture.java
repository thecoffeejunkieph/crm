package ph.thecoffeejunkie.crm.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds tagged users, customers, warehouses, products, orders and on-hand stock for the inventory ITs.
 * Stock is received through {@link StockLedger}. The movement ledger is append-only for crm_app, so the
 * products and warehouses it references are archived on clean-up rather than deleted.
 */
final class InventoryFixture {

    record Line(long productId, String qty) {}

    final String tag = "IF" + UUID.randomUUID().toString().substring(0, 8);
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

    static Line l(long productId, String qty) {
        return new Line(productId, qty);
    }

    long user(String name) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, ?, 'U', 'SALES') returning id", Long.class, tag + name + "@if.it", name);
    }

    long warehouse(String code) {
        return jdbc.queryForObject("insert into warehouse (code, name) values (?, 'Fixture') returning id",
                Long.class, tag + code);
    }

    long product(String sku, String unit) {
        return jdbc.queryForObject("insert into product (sku, name, unit, price, tax_class) "
                + "values (?, ?, ?, 10, 'VATABLE') returning id", Long.class, tag + sku, "Product " + sku, unit);
    }

    void receive(long productId, long warehouseId, String qty) {
        receive(productId, warehouseId, qty, null);
    }

    void receive(long productId, long warehouseId, String qty, Long linkedOrderId) {
        tx.executeWithoutResult(s -> ledger.post(MovementType.RECEIPT, productId, warehouseId, new BigDecimal(qty),
                "TEST", 0, null, linkedOrderId));
    }

    /** A CONFIRMED order with real order lines; the returned stock request lists them in insertion order. */
    OrderStock order(long repId, long warehouseId, Instant confirmedAt, Line... lines) {
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

    BigDecimal onHand(long productId, long warehouseId) {
        return level(productId, warehouseId, "on_hand");
    }

    BigDecimal reserved(long productId, long warehouseId) {
        return level(productId, warehouseId, "reserved");
    }

    private BigDecimal level(long productId, long warehouseId, String column) {
        return jdbc.queryForObject("select coalesce((select " + column + " from stock_level where product_id = ? "
                + "and warehouse_id = ?), 0)", BigDecimal.class, productId, warehouseId);
    }

    List<String> notificationTitles(long userId) {
        return jdbc.queryForList("select title from notification where recipient_user_id = ? "
                + "and type = 'ORDER_RESERVED' order by id", String.class, userId);
    }

    void cleanUp() {
        String orderIds = "select id from sales_order where customer_id = " + customer;
        String products = "select id from product where sku like '" + tag + "%'";
        jdbc.update("delete from stock_reservation where sales_order_id in (" + orderIds + ")");
        jdbc.update("delete from stock_level where product_id in (" + products + ")");
        jdbc.update("delete from notification where recipient_user_id in (select id from app_user where email like ?)",
                tag + "%");
        jdbc.update("delete from order_line where order_id in (" + orderIds + ")");
        jdbc.update("delete from sales_order where customer_id = ?", customer);
        jdbc.update("delete from customer where id = ?", customer);
        jdbc.update("delete from app_user where email like ?", tag + "%");
        jdbc.update("update product set archived_at = now() where sku like ?", tag + "%");
        jdbc.update("update warehouse set archived_at = now() where code like ?", tag + "%");
    }
}
