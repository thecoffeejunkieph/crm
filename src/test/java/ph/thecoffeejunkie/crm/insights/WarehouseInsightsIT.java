package ph.thecoffeejunkie.crm.insights;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;

/** The warehouse user works fresh warehouses, so their counts are exact; ADMIN (all warehouses) is checked by delta. */
@IntegrationTest
class WarehouseInsightsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    InsightsFixture f;
    long admin;
    long user;
    long w1;
    long w2;
    long cust;

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        user = f.user("wh", Role.WAREHOUSE);
        w1 = f.warehouse("W1");
        w2 = f.warehouse("W2");
        cust = f.customer("acme", null);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions asUser() throws Exception {
        caches.getCache("insights").invalidate();
        return mvc.perform(get("/api/insights/warehouse").with(as(user, Role.WAREHOUSE, Set.of(w1))));
    }

    private String asAdmin() throws Exception {
        caches.getCache("insights").invalidate();
        return mvc.perform(get("/api/insights/warehouse").with(as(admin, Role.ADMIN))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long delivery(long warehouse, String status, Instant deliveredAt) {
        long o = f.order(cust, null, "RELEASED", "100.00", "0.00", null);
        return f.delivery(o, warehouse, status, deliveredAt);
    }

    @Test
    void countsOnlyAssignedWarehouses() throws Exception {
        String before = asAdmin();
        delivery(w1, "PICKING", null);
        delivery(w2, "PICKING", null);
        delivery(w1, "DISPATCHED", null);
        asUser().andExpect(status().isOk())
                .andExpect(jsonPath("$.today.toPick").value(1))
                .andExpect(jsonPath("$.today.dispatched").value(1));
        int b = JsonPath.read(before, "$.today.toPick");
        int a = JsonPath.read(asAdmin(), "$.today.toPick");
        assertThat(a - b).isEqualTo(2);
    }

    @Test
    void deliveredTodayUsesManilaDate() throws Exception {
        Instant startOfToday = LocalDate.now(InsightsMonth.MANILA).atStartOfDay(InsightsMonth.MANILA).toInstant();
        delivery(w1, "DELIVERED", startOfToday.plusSeconds(1800));
        delivery(w1, "DELIVERED", startOfToday.minusSeconds(1800));
        asUser().andExpect(jsonPath("$.today.deliveredToday").value(1));
    }

    @Test
    void transfersFromAndTo() throws Exception {
        f.transfer(w1, w2, "DRAFT");
        f.transfer(w2, w1, "DISPATCHED");
        f.transfer(w2, w1, "DRAFT");
        f.transfer(w1, w2, "DISPATCHED");
        asUser().andExpect(jsonPath("$.transfers.toDispatch").value(1))
                .andExpect(jsonPath("$.transfers.incoming").value(1));
    }

    @Test
    void queueOldestTenNoMoney() throws Exception {
        long oldest = 0;
        for (int i = 0; i < 11; i++) {
            long d = delivery(w1, i % 2 == 0 ? "PICKING" : "DISPATCHED", null);
            jdbc.update("update delivery_order set created_at = now() - make_interval(hours => ?) where id = ?", 50 - i, d);
            if (i == 0) {
                oldest = d;
            }
        }
        delivery(w1, "DELIVERED", Instant.now());
        String body = asUser().andExpect(jsonPath("$.queue.length()").value(10))
                .andExpect(jsonPath("$.queue[0].id").value(oldest))
                .andExpect(jsonPath("$.queue[0].status").value("PICKING"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("total").doesNotContain("amount").doesNotContain("collected");
    }

    @Test
    void noWarehousesMeansZeros() throws Exception {
        delivery(w1, "PICKING", null);
        caches.getCache("insights").invalidate();
        mvc.perform(get("/api/insights/warehouse").with(as(user, Role.WAREHOUSE, Set.of())))
                .andExpect(jsonPath("$.today.toPick").value(0))
                .andExpect(jsonPath("$.queue.length()").value(0));
    }

    @Test
    void salesForbidden() throws Exception {
        long rep = f.user("rep", Role.SALES);
        mvc.perform(get("/api/insights/warehouse").with(as(rep, Role.SALES))).andExpect(status().isForbidden());
    }
}
