package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Runs as crm_app and rolls back. A failed statement aborts the transaction, so each test fails at most once, last. */
@IntegrationTest
@Transactional
class InventorySchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    long userId;
    long productId;
    long warehouseId;
    long otherWarehouseId;

    @BeforeEach
    void seed() {
        userId = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'S', 'R', 'SALES') returning id", Long.class, UUID.randomUUID() + "@inv.it");
        productId = jdbc.queryForObject("insert into product (sku, name, unit, price, tax_class) "
                + "values (?, 'P', 'PC', 1, 'VATABLE') returning id", Long.class, UUID.randomUUID().toString());
        warehouseId = warehouse(false);
        otherWarehouseId = warehouse(false);
    }

    private long warehouse(boolean isDefault) {
        return jdbc.queryForObject("insert into warehouse (code, name, is_default) values (?, 'W', ?) returning id",
                Long.class, UUID.randomUUID().toString(), isDefault);
    }

    private long order(Long shipFrom) {
        long customerId = jdbc.queryForObject(
                "insert into customer (type, display_name) values ('BUSINESS', 'Acme') returning id", Long.class);
        return jdbc.queryForObject("insert into sales_order (number, customer_id, rep_id, status, payment_terms, "
                + "created_by, ship_from_warehouse_id) values (?, ?, ?, 'CONFIRMED', 'NET_30', ?, ?) returning id",
                Long.class, "SO-" + UUID.randomUUID(), customerId, userId, userId, shipFrom);
    }

    private void deliveryOrder(long orderId, String status) {
        jdbc.update("insert into delivery_order (number, sales_order_id, warehouse_id, method, status, customer_name) "
                + "values (?, ?, ?, 'DELIVERY', ?, 'Acme')", "DO-" + UUID.randomUUID(), orderId, warehouseId, status);
    }

    @Test
    void reservedAboveOnHandRejected() {
        jdbc.update("insert into stock_level (product_id, warehouse_id, on_hand, reserved) values (?, ?, 5, 5)",
                productId, warehouseId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into stock_level (product_id, warehouse_id, on_hand, reserved) values (?, ?, 5, 5.001)",
                productId, otherWarehouseId));
    }

    @Test
    void movementCannotBeUpdatedOrDeleted() {
        jdbc.update("insert into stock_movement (product_id, warehouse_id, type, qty) values (?, ?, 'RECEIPT', 1)",
                productId, warehouseId);
        DataAccessException up = assertThrows(DataAccessException.class,
                () -> jdbc.update("update stock_movement set qty = 2"));
        assertThat(up.getMostSpecificCause().getMessage()).contains("permission denied");
    }

    @Test
    void movementCannotBeDeleted() {
        DataAccessException del = assertThrows(DataAccessException.class,
                () -> jdbc.update("delete from stock_movement"));
        assertThat(del.getMostSpecificCause().getMessage()).contains("permission denied");
    }

    @Test
    void secondLiveDeliveryOrderPerOrderRejected() {
        long orderId = order(warehouseId);
        deliveryOrder(orderId, "CANCELLED");
        deliveryOrder(orderId, "PICKING");
        assertThrows(DataIntegrityViolationException.class, () -> deliveryOrder(orderId, "PICKING"));
    }

    @Test
    void transferToSameWarehouseRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into stock_transfer (number, from_warehouse_id, to_warehouse_id, status) "
                        + "values (?, ?, ?, 'DRAFT')", "TR-" + UUID.randomUUID(), warehouseId, warehouseId));
    }

    @Test
    void existingOrdersBackfilledWithDefaultWarehouse() {
        // V5 ran before this test, so exercise the same default for an insert that names no warehouse.
        Long existing = jdbc.query("select id from warehouse where is_default and archived_at is null",
                rs -> rs.next() ? rs.getLong(1) : null);
        long defaultId = existing != null ? existing : warehouse(true);

        long orderId = order(null);

        assertThat(jdbc.queryForObject("select ship_from_warehouse_id from sales_order where id = ?", Long.class,
                orderId)).isEqualTo(defaultId);
        assertThat(jdbc.queryForObject("select delivery_method from sales_order where id = ?", String.class,
                orderId)).isEqualTo("DELIVERY");
    }

    @Test
    void reservationNeedsSomeQuantity() {
        long orderId = order(warehouseId);
        long lineId = jdbc.queryForObject("insert into order_line (order_id, position, product_id, description, "
                + "unit, qty, unit_price, discount_pct, tax_class, line_total) "
                + "values (?, 0, ?, 'd', 'PC', 1, 1, 0, 'VATABLE', 0) returning id", Long.class, orderId, productId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into stock_reservation (order_line_id, sales_order_id, order_number, product_id, "
                        + "warehouse_id, reserved_qty, short_qty, confirmed_at) values (?, ?, 'SO-1', ?, ?, 0, 0, now())",
                lineId, orderId, productId, warehouseId));
    }

    @Test
    void countersSeeded() {
        List<String> names = jdbc.queryForList("select name from document_counter where next_value >= 1 "
                + "and name in ('STOCK_RECEIPT','STOCK_ADJUSTMENT','STOCK_TRANSFER','DELIVERY_ORDER')", String.class);
        assertThat(names).containsExactlyInAnyOrder("STOCK_RECEIPT", "STOCK_ADJUSTMENT", "STOCK_TRANSFER",
                "DELIVERY_ORDER");
    }
}
