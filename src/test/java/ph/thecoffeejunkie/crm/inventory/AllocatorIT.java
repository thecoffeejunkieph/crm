package ph.thecoffeejunkie.crm.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class AllocatorIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long rep;
    long wh;
    Instant t0 = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        wh = f.warehouse("W");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    @Test
    void linkedOrderFilledFirst() {
        long p = f.product("P", "PC");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));
        OrderStock b = f.order(rep, wh, t0.plusSeconds(60), l(p, "5"));
        inventory.reserve(a);
        inventory.reserve(b);

        f.receive(p, wh, "5", b.orderId());

        assertThat(inventory.totalShort(b.orderId())).isEqualByComparingTo("0");
        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("5");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("5");
    }

    @Test
    void thenOldestConfirmed() {
        long p = f.product("P", "PC");
        // a has the lower id but was confirmed later.
        OrderStock a = f.order(rep, wh, t0.plusSeconds(60), l(p, "5"));
        OrderStock b = f.order(rep, wh, t0, l(p, "5"));
        inventory.reserve(a);
        inventory.reserve(b);

        f.receive(p, wh, "5");

        assertThat(inventory.totalShort(b.orderId())).isEqualByComparingTo("0");
        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("5");
    }

    @Test
    void partialFillLeavesRemainderShort() {
        long p = f.product("P", "PC");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));
        inventory.reserve(a);

        f.receive(p, wh, "3");

        LineStock s = inventory.stockFor(a.orderId()).get(a.lines().getFirst().orderLineId());
        assertThat(s.reservedQty()).isEqualByComparingTo("3");
        assertThat(s.shortQty()).isEqualByComparingTo("2");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");
        assertThat(f.notificationTitles(rep)).isEmpty();
    }

    @Test
    void fractionalQuantities() {
        long p = f.product("BEANS", "KG");
        OrderStock a = f.order(rep, wh, t0, l(p, "1.25"));
        OrderStock b = f.order(rep, wh, t0.plusSeconds(60), l(p, "1.25"));
        inventory.reserve(a);
        inventory.reserve(b);

        f.receive(p, wh, "2.5");

        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("0");
        assertThat(inventory.totalShort(b.orderId())).isEqualByComparingTo("0");
        BigDecimal available = f.onHand(p, wh).subtract(f.reserved(p, wh));
        assertThat(available).isEqualTo(new BigDecimal("0.000"));
    }

    @Test
    void fullyReservedNotifiesRepAfterCommit() {
        long p1 = f.product("P1", "PC");
        long p2 = f.product("P2", "PC");
        OrderStock a = f.order(rep, wh, t0, l(p1, "2"), l(p2, "2"));
        inventory.reserve(a);

        f.receive(p1, wh, "2");
        assertThat(f.notificationTitles(rep)).as("still short on p2").isEmpty();

        tx.executeWithoutResult(s -> {
            ledger.post(MovementType.RECEIPT, p2, wh, new BigDecimal("2"), "TEST", 0, null, null);
            s.setRollbackOnly();
        });
        assertThat(f.notificationTitles(rep)).as("rolled back").isEmpty();
        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("2");

        f.receive(p2, wh, "2");
        assertThat(f.notificationTitles(rep)).containsExactly("Order " + a.orderNumber() + " is fully reserved");
    }
}
