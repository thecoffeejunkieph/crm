package ph.thecoffeejunkie.crm.purchasing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ReorderReminderIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;
    @Autowired ReorderReminderSchedule schedule;

    PurchasingFixture f;
    long wh;
    long admin;

    @BeforeEach
    void seed() {
        f = new PurchasingFixture(jdbc, tx, ctx);
        jdbc.update("delete from reorder_reminder_state");
        wh = f.inv.warehouse("A");
        admin = f.inv.admin("adm");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from notification where recipient_user_id = ?", admin);
        jdbc.update("delete from reorder_reminder_state");
        f.cleanUp();
    }

    /** Level 10, target 30, on hand 4: due for reorder. */
    private long product(String sku, Long supplierId) {
        long id = f.inv.product(sku, "PC");
        jdbc.update("update product set reorder_level = 10, reorder_target = 30, preferred_supplier_id = ? where id = ?",
                supplierId, id);
        f.inv.receive(id, wh, "4");
        return id;
    }

    private List<Map<String, Object>> sent() {
        return jdbc.queryForList("select title, body, entity_type, entity_id from notification "
                + "where recipient_user_id = ? and type = 'REORDER_DUE' order by id", admin);
    }

    @Test
    void notifiesAdminsWhenItemsDue() {
        long a = f.supplier("A", null);
        long b = f.supplier("B", null);
        product("1", a);
        product("2", a);
        product("3", b);
        product("4", null);

        schedule.run();

        assertThat(sent()).singleElement().satisfies(n -> {
            assertThat(n.get("title")).isEqualTo("4 items to reorder across 3 suppliers");
            assertThat(n.get("body")).isEqualTo("Supplier A, Supplier B, No supplier");
            assertThat(n.get("entity_type")).isEqualTo("REORDER");
            assertThat(n.get("entity_id")).isNull();
        });
    }

    @Test
    void singularTitleAndMoreSuppliersInBody() {
        product("1", null);
        schedule.run();
        assertThat(sent()).singleElement().satisfies(n -> {
            assertThat(n.get("title")).isEqualTo("1 item to reorder across 1 supplier");
            assertThat(n.get("body")).isEqualTo("No supplier");
        });
        jdbc.update("delete from notification where recipient_user_id = ?", admin);
        jdbc.update("delete from reorder_reminder_state");
        for (String c : List.of("B", "C", "D")) {
            product(c, f.supplier(c, null));
        }
        schedule.run();
        assertThat(sent()).singleElement().satisfies(n ->
                assertThat(n.get("body")).isEqualTo("Supplier B, Supplier C, Supplier D and 1 more"));
    }

    @Test
    void noNotificationWhenNothingDue() {
        schedule.run();
        assertThat(sent()).isEmpty();
    }

    @Test
    void noRepeatWhenSameSet() {
        product("1", null);
        schedule.run();
        schedule.run();
        assertThat(sent()).hasSize(1);
    }

    @Test
    void notifiesAgainWhenSetChanges() {
        product("1", null);
        schedule.run();
        product("2", null);
        schedule.run();
        assertThat(sent()).hasSize(2);
        assertThat(sent().get(1).get("title")).isEqualTo("2 items to reorder across 1 supplier");
    }
}
