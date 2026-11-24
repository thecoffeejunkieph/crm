package ph.thecoffeejunkie.crm.purchasing;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.inventory.InventoryFixture;

import java.math.BigDecimal;

/**
 * Wraps {@link InventoryFixture} (users, warehouses, products, stock) and adds suppliers. Takes the application
 * context instead of StockLedger, which is package-private to inventory.
 */
final class PurchasingFixture {

    final InventoryFixture inv;
    private final JdbcTemplate jdbc;

    PurchasingFixture(JdbcTemplate jdbc, TransactionTemplate tx, ApplicationContext ctx) {
        this.jdbc = jdbc;
        this.inv = InventoryFixture.create(jdbc, tx, ctx);
    }

    String code(String suffix) {
        return inv.tag + suffix;
    }

    long supplier(String code, BigDecimal limit) {
        return jdbc.queryForObject("insert into supplier (code, name, credit_limit) values (?, ?, ?) returning id",
                Long.class, inv.tag + code, "Supplier " + code, limit);
    }

    void cleanUp() {
        jdbc.update("delete from purchase_order where supplier_id in (select id from supplier where code like ?)", inv.tag + "%");
        jdbc.update("delete from supplier where code like ?", inv.tag + "%");
        inv.cleanUp();
    }
}
