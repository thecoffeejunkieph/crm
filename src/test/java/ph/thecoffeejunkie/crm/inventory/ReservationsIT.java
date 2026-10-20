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
