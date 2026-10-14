package ph.thecoffeejunkie.crm.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

@IntegrationTest
class InvoiceNumberingIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;
    @Autowired InvoiceService invoices;
    @Autowired TransactionTemplate tx;

    SalesFixture f;
    long admin;

    @BeforeEach
    void seed() {
        InvoicesIT.companySettings(jdbc, caches, "The Coffee Junkie Inc.", "123", "Makati");
        f = new SalesFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
        InvoicesIT.companySettings(jdbc, caches, "", "", "");
    }

    @Test
    void concurrentIssuesAreGaplessAndUnique() throws Exception {
        long customer = f.customer("acme", null);
        long product = f.product("A", "10.00", null, "VATABLE");
        List<Long> orders = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            long id = f.createdOrder(as(admin, Role.ADMIN), customer, line(product, "1"));
            f.setStatus(id, "CONFIRMED");
            orders.add(id);
        }
        Scope scope = new Scope(admin, Role.ADMIN, Set.of());
        long rolledBack = orders.removeLast();
        ExecutorService pool = Executors.newFixedThreadPool(13);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        for (long id : orders) {
            done.add(pool.submit(() -> {
                SalesFixture.login(admin, Role.ADMIN);
                gate.await();
                invoices.issue(scope, id, new TransitionRequest(f.version(id), null));
                return null;
            }));
        }
        done.add(pool.submit(() -> {
            SalesFixture.login(admin, Role.ADMIN);
            gate.await();
            tx.executeWithoutResult(s -> {
                invoices.issue(scope, rolledBack, new TransitionRequest(f.version(rolledBack), null));
                s.setRollbackOnly();
            });
            return null;
        }));
        gate.countDown();
        for (Future<?> d : done) {
            d.get();
        }
        pool.shutdown();

        List<Integer> serials = jdbc.queryForList("select substring(number from 4)::int from invoice where order_id in ("
                + String.join(",", orders.stream().map(String::valueOf).toList()) + ") order by 1", Integer.class);
        assertThat(serials).hasSize(12).doesNotHaveDuplicates();
        assertThat(serials.getLast() - serials.getFirst()).isEqualTo(11);
        assertThat(jdbc.queryForObject("select count(*) from invoice where order_id = ?", Integer.class, rolledBack))
                .isZero();
        assertThat(jdbc.queryForObject("select next_value from document_counter where name = 'INVOICE'", Integer.class))
                .isEqualTo(serials.getLast() + 1);
    }
}
