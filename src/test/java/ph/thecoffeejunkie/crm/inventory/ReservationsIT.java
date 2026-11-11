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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.l;

@IntegrationTest
class ReservationsIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired StockLedger ledger;
    @Autowired Inventory inventory;

    InventoryFixture f;
    long rep;
    long wh;
    long p;
    Instant t0 = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void seed() {
        f = new InventoryFixture(jdbc, tx, ledger);
        rep = f.user("rep");
        wh = f.warehouse("W");
        p = f.product("P", "KG");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    @Test
    void crossingIntoLowNotifiesAdminsAndShipFromWarehouse() {
        long admin = f.admin("adm");
        long picker = f.warehouseUser("pick", wh);
        long elsewhere = f.warehouseUser("far", f.warehouse("X"));
        f.reorderLevel(p, "5");
        f.receive(p, wh, "10");

        inventory.reserve(f.order(rep, wh, t0, l(p, "6")));

        assertThat(f.notifications(admin)).containsExactly("LOW_STOCK|PRODUCT|" + p);
        assertThat(f.notifications(picker)).containsExactly("LOW_STOCK|PRODUCT|" + p);
        assertThat(f.notifications(elsewhere)).isEmpty();
        assertThat(jdbc.queryForObject("select title || '|' || body from notification where recipient_user_id = ?",
                String.class, admin)).isEqualTo(f.tag + "P is low on stock|4 kg available, reorder level 5.");
    }

    @Test
    void stayingBelowLevelDoesNotRepeat() {
        long admin = f.admin("adm");
        f.reorderLevel(p, "5");
        f.receive(p, wh, "10");
        inventory.reserve(f.order(rep, wh, t0, l(p, "6")));

        inventory.reserve(f.order(rep, wh, t0, l(p, "1")));

        assertThat(f.notifications(admin)).containsExactly("LOW_STOCK|PRODUCT|" + p);
    }

    @Test
    void crossingToZeroSendsOutOnly() {
        long admin = f.admin("adm");
        f.reorderLevel(p, "5");
        f.receive(p, wh, "10");
        OrderStock a = f.order(rep, wh, t0, l(p, "10"));

        inventory.reserve(a);

        assertThat(f.notifications(admin)).containsExactly("OUT_OF_STOCK|PRODUCT|" + p);
        assertThat(jdbc.queryForObject("select title || '|' || body from notification where recipient_user_id = ?",
                String.class, admin)).isEqualTo(f.tag + "P is out of stock|Order " + a.orderNumber() + " took the last of it.");
    }

    @Test
    void noReorderLevelNeverLow() {
        long admin = f.admin("adm");
        f.receive(p, wh, "10");

        inventory.reserve(f.order(rep, wh, t0, l(p, "6")));

        assertThat(f.notifications(admin)).isEmpty();
    }

    @Test
    void adminInWarehouseGetsOne() {
        long admin = f.admin("adm");
        f.member(admin, wh);
        f.reorderLevel(p, "5");
        f.receive(p, wh, "10");

        inventory.reserve(f.order(rep, wh, t0, l(p, "6")));

        assertThat(f.notifications(admin)).containsExactly("LOW_STOCK|PRODUCT|" + p);
    }

    @Test
    void shortReservationNotifiesAdmins() {
        long admin = f.admin("adm");
        f.receive(p, wh, "3");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));

        inventory.reserve(a);

        assertThat(f.notifications(admin)).containsExactly("OUT_OF_STOCK|PRODUCT|" + p, "ORDER_SHORT|ORDER|" + a.orderId());
        assertThat(jdbc.queryForObject("select title || '|' || body from notification where recipient_user_id = ? "
                + "and type = 'ORDER_SHORT'", String.class, admin))
                .isEqualTo("Order " + a.orderNumber() + " is short|2 short after reservation.");
    }

    @Test
    void reserveTakesAvailableRestShort() {
        f.receive(p, wh, "3");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));

        inventory.reserve(a);

        assertLine(a, 0, "3", "2");
        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("2");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");
    }

    @Test
    void concurrentConfirmsNeverOversell() throws Exception {
        f.receive(p, wh, "5");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));
        OrderStock b = f.order(rep, wh, t0, l(p, "5"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            // Each call is its own transaction on its own thread; Inventory.reserve is @Transactional.
            List<Future<Object>> done = List.of(a, b).stream().map(o -> pool.submit(() -> {
                gate.await();
                inventory.reserve(o);
                return null;
            })).toList();
            gate.countDown();
            for (Future<Object> d : done) {
                d.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<BigDecimal> shorts = List.of(inventory.totalShort(a.orderId()), inventory.totalShort(b.orderId()));
        assertThat(shorts).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(BigDecimal.ZERO, new BigDecimal("5"));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("5");
        assertThat(f.onHand(p, wh)).isGreaterThanOrEqualTo(f.reserved(p, wh));
    }

    @Test
    void sameProductTwoLines() {
        f.receive(p, wh, "6");
        OrderStock a = f.order(rep, wh, t0, l(p, "4"), l(p, "4"));

        inventory.reserve(a);

        assertLine(a, 0, "4", "0");
        assertLine(a, 1, "2", "2");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("6");
        assertThat(f.onHand(p, wh)).isEqualByComparingTo("6");
    }

    @Test
    void adjustDecreaseFreesShortFirst() {
        f.receive(p, wh, "3");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));
        inventory.reserve(a);

        inventory.adjust(withQty(a, "4"));
        assertLine(a, 0, "3", "1");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");

        inventory.adjust(withQty(a, "2"));
        assertLine(a, 0, "2", "0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("2");

        inventory.adjust(withQty(a, "4"));
        assertLine(a, 0, "3", "1");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");
    }

    @Test
    void adjustRemovedLineFreesAndReallocates() {
        long p2 = f.product("P2", "PC");
        f.receive(p, wh, "5");
        f.receive(p2, wh, "1");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"), l(p2, "1"));
        OrderStock b = f.order(rep, wh, t0.plusSeconds(60), l(p, "3"));
        inventory.reserve(a);
        inventory.reserve(b);
        assertThat(inventory.totalShort(b.orderId())).isEqualByComparingTo("3");

        // Drop a's first line, keep the second.
        inventory.adjust(new OrderStock(a.orderId(), a.orderNumber(), a.repUserId(), a.warehouseId(),
                a.confirmedAt(), List.of(a.lines().get(1))));

        assertThat(inventory.stockFor(a.orderId())).containsOnlyKeys(a.lines().get(1).orderLineId());
        assertThat(inventory.totalShort(b.orderId())).isEqualByComparingTo("0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("3");
        assertThat(f.reserved(p2, wh)).isEqualByComparingTo("1");
    }

    @Test
    void adjustNewWarehouseMovesReservations() {
        long wh2 = f.warehouse("W2");
        f.receive(p, wh, "5");
        f.receive(p, wh2, "5");
        OrderStock a = f.order(rep, wh, t0, l(p, "4"));
        inventory.reserve(a);

        inventory.adjust(new OrderStock(a.orderId(), a.orderNumber(), a.repUserId(), wh2, a.confirmedAt(),
                a.lines()));

        assertLine(a, 0, "4", "0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("0");
        assertThat(f.reserved(p, wh2)).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("select warehouse_id from stock_reservation where sales_order_id = ?",
                Long.class, a.orderId())).isEqualTo(wh2);
    }

    @Test
    void releaseFreesAndReallocates() {
        f.receive(p, wh, "5");
        OrderStock a = f.order(rep, wh, t0, l(p, "5"));
        OrderStock b = f.order(rep, wh, t0.plusSeconds(60), l(p, "2"));
        inventory.reserve(a);
        inventory.reserve(b);

        inventory.release(a.orderId());

        assertThat(inventory.stockFor(a.orderId())).isEmpty();
        assertThat(inventory.totalShort(a.orderId())).isEqualByComparingTo("0");
        assertLine(b, 0, "2", "0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("2");
        assertThat(f.notificationTitles(rep)).containsExactly("Order " + b.orderNumber() + " is fully reserved");
    }

    @Test
    void releaseAfterConcurrentAllocateUsesFreshRows() throws Exception {
        f.receive(p, wh, "2");
        OrderStock b = f.order(rep, wh, t0, l(p, "5"));
        OrderStock c = f.order(rep, wh, t0.plusSeconds(60), l(p, "4"));
        inventory.reserve(b);
        inventory.reserve(c);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch aLocked = new CountDownLatch(1);
        CountDownLatch bBlocked = new CountDownLatch(1);
        try {
            // A holds the level lock until B is waiting on it, then receives 3, which fills b's short rows.
            Future<Object> a = pool.submit(() -> {
                tx.executeWithoutResult(s -> {
                    ledger.lock(p, wh);
                    aLocked.countDown();
                    await(bBlocked);
                    ledger.post(MovementType.RECEIPT, p, wh, new BigDecimal("3"), "TEST", 0, null, null);
                });
                return null;
            });
            await(aLocked);
            Future<Object> rel = pool.submit(() -> {
                inventory.release(b.orderId());
                return null;
            });
            waitForBlockedLevelLock();
            bBlocked.countDown();
            a.get(30, TimeUnit.SECONDS);
            rel.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(inventory.stockFor(b.orderId())).isEmpty();
        assertLine(c, 0, "4", "0");
        assertThat(f.reserved(p, wh)).isEqualByComparingTo(jdbc.queryForObject("select coalesce(sum(reserved_qty), 0) "
                + "from stock_reservation where product_id = ? and warehouse_id = ?", BigDecimal.class, p, wh));
        assertThat(f.reserved(p, wh)).isEqualByComparingTo("4");
    }

    /** Polls until another session waits on a row lock taken by a stock_level FOR UPDATE. */
    private void waitForBlockedLevelLock() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' "
                    + "and query ilike '%from stock_level%for%update%'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("release never blocked on the level lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertLine(OrderStock o, int index, String reserved, String shortQty) {
        LineStock s = inventory.stockFor(o.orderId()).get(o.lines().get(index).orderLineId());
        assertThat(s.reservedQty()).as("reserved").isEqualByComparingTo(reserved);
        assertThat(s.shortQty()).as("short").isEqualByComparingTo(shortQty);
    }

    private static OrderStock withQty(OrderStock o, String qty) {
        ReserveLine l = o.lines().getFirst();
        return new OrderStock(o.orderId(), o.orderNumber(), o.repUserId(), o.warehouseId(), o.confirmedAt(),
                List.of(new ReserveLine(l.orderLineId(), l.productId(), new BigDecimal(qty))));
    }
}
