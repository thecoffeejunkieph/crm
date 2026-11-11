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

import java.sql.Timestamp;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/**
 * Company-wide figures: money is asserted on a quiet month (20 months back) no other test writes to; live counts
 * (operations) as before/after deltas.
 */
@IntegrationTest
class AdminInsightsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    InsightsFixture f;
    long admin;
    long repA;
    long repB;
    long custA;
    long custNone;
    final YearMonth q = YearMonth.now(InsightsMonth.MANILA).minusMonths(20);

    @BeforeEach
    void seed() {
        caches.getCache("insights").invalidate();
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        repA = f.user("repa", Role.SALES);
        repB = f.user("repb", Role.SALES);
        custA = f.customer("acme", repA);
        custNone = f.customer("walkin", null);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions adminView(YearMonth m) throws Exception {
        caches.getCache("insights").invalidate();
        return mvc.perform(get("/api/insights/admin").param("month", m.toString()).with(as(admin, Role.ADMIN)));
    }

    private static String rep(long id) {
        return "$.reps[?(@.repId == " + id + ")]";
    }

    /** Verified payments in q: 1000 for rep A, 500 unassigned; 300 in the month before. */
    private void payments() {
        long a = f.order(custA, repA, "FULFILLED", "5000.00", "0.00", null);
        long none = f.order(custNone, null, "FULFILLED", "5000.00", "0.00", null);
        f.payment(a, "VERIFIED", "1000.00", "0.00", at(q, 30));
        f.payment(none, "VERIFIED", "500.00", "0.00", at(q, 30));
        f.payment(a, "VERIFIED", "300.00", "0.00", at(q.minusMonths(1), 30));
    }

    @Test
    void companyCollectedAndPrevMonth() throws Exception {
        payments();
        adminView(q).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(q.toString()))
                .andExpect(jsonPath("$.collected").value(1500.00))
                .andExpect(jsonPath("$.collectedPrevMonth").value(300.00));
    }

    @Test
    void repsTableIncludesUnassignedAndZeroReps() throws Exception {
        payments();
        jdbc.update("insert into sales_target (rep_id, month, target_amount, commission_pct, updated_by) "
                + "values (?, ?, 1000, 2, ?)", repA, q.atDay(1), admin);
        adminView(q)
                .andExpect(jsonPath(rep(repA) + ".collected").value(contains(1000.0)))
                .andExpect(jsonPath(rep(repA) + ".target").value(contains(1000.0)))
                .andExpect(jsonPath(rep(repA) + ".commission").value(contains(20.0)))
                .andExpect(jsonPath(rep(repA) + ".targetHit").value(contains(true)))
                .andExpect(jsonPath(rep(repA) + ".progressPct").value(contains(100)))
                .andExpect(jsonPath(rep(repB) + ".collected").value(contains(0.0)))
                .andExpect(jsonPath(rep(repB) + ".target").value(contains((Object) null)))
                .andExpect(jsonPath("$.reps[?(@.repId == null)].name").value(contains("Unassigned")))
                .andExpect(jsonPath("$.reps[?(@.repId == null)].collected").value(contains(500.0)));
    }

    @Test
    void departedRepStillListedForPastMonth() throws Exception {
        payments();
        jdbc.update("update app_user set archived_at = now() where id = ?", repA);
        adminView(q).andExpect(jsonPath(rep(repA) + ".collected").value(contains(1000.0)))
                .andExpect(jsonPath(rep(repA) + ".name").value(contains(f.tag + "repa U")));
    }

    @Test
    void marginSkipsLinesWithoutCost() throws Exception {
        long o = f.order(custA, repA, "FULFILLED", "1500.00", "0.00", null);
        f.line(o, f.product("costed", "600.00"), "1", "1000.00", "600.00");
        f.line(o, f.product("uncosted", null), "1", "500.00", null);
        f.invoice(o, "ISSUED", at(q, 30), "1500.00");
        confirmedIn(o);
        adminView(q).andExpect(jsonPath("$.margin.amount").value(400.00))
                .andExpect(jsonPath("$.margin.pct").value(40.00))
                .andExpect(jsonPath("$.margin.linesWithoutCost").value(1))
                .andExpect(jsonPath("$.invoiced").value(1500.00));
    }

    @Test
    void marginIgnoresUnbilledOrders() throws Exception {
        for (String status : new String[] {"DRAFT", "CANCELLED"}) {
            long o = f.order(custA, repA, status, "1000.00", "0.00", null);
            f.line(o, f.product("v" + status, "100.00"), "1", "1000.00", "100.00");
            confirmedIn(o);
        }
        adminView(q).andExpect(jsonPath("$.margin.amount").value(0.00))
                .andExpect(jsonPath("$.margin.pct").isEmpty())
                .andExpect(jsonPath("$.margin.linesWithoutCost").value(0));
    }

    @Test
    void collectedByMonthHasTwelveEndingAtMonth() throws Exception {
        payments();
        adminView(q).andExpect(jsonPath("$.collectedByMonth.length()").value(12))
                .andExpect(jsonPath("$.collectedByMonth[11].month").value(q.toString()))
                .andExpect(jsonPath("$.collectedByMonth[11].amount").value(1500.00))
                .andExpect(jsonPath("$.collectedByMonth[10].amount").value(300.00))
                .andExpect(jsonPath("$.collectedByMonth[0].month").value(q.minusMonths(11).toString()));
    }

    @Test
    void topProductsByRevenue() throws Exception {
        long o = f.order(custA, repA, "FULFILLED", "3000.00", "0.00", null);
        long small = f.product("small", "50.00");
        long big = f.product("big", "1500.00");
        f.line(o, small, "1", "100.00", "50.00");
        f.line(o, big, "2", "2900.00", "1000.00");
        confirmedIn(o); // no invoice yet: still counted, like Collected
        adminView(q).andExpect(jsonPath("$.topProducts[0].productId").value(big))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(2900.00))
                .andExpect(jsonPath("$.topProducts[0].margin").value(900.00))
                .andExpect(jsonPath("$.topProducts[1].productId").value(small))
                .andExpect(jsonPath("$.topProducts[1].margin").value(50.00));
    }

    @Test
    void marginIsExVatAndWithoutShipping() throws Exception {
        // 1,120 of VATable goods + 112 shipping: 1,000 + 100 net, 132 VAT, 1,232 total.
        long o = f.order(custA, repA, "CONFIRMED", "1232.00", "0.00", null);
        f.set(o, "vat_exempt_sales", java.math.BigDecimal.ZERO);
        f.set(o, "vatable_sales", new java.math.BigDecimal("1100.00"));
        f.set(o, "vat", new java.math.BigDecimal("132.00"));
        f.set(o, "shipping_fee", new java.math.BigDecimal("112.00"));
        f.line(o, f.product("vat", "600.00"), "1", "1120.00", "600.00");
        confirmedIn(o);
        adminView(q).andExpect(jsonPath("$.margin.amount").value(400.00))
                .andExpect(jsonPath("$.topProducts[0].revenue").value(1000.00));
    }

    private void confirmedIn(long orderId) {
        f.set(orderId, "confirmed_at", Timestamp.from(at(q, 30)));
    }

    @Test
    void operationsCountsAreDeltas() throws Exception {
        YearMonth now = YearMonth.now(InsightsMonth.MANILA);
        String before = adminView(now).andReturn().getResponse().getContentAsString();
        long wh = f.defaultWarehouse();
        long other = f.warehouse("W2");
        long o = f.order(custA, repA, "RELEASED", "100.00", "0.00", null);
        f.delivery(o, wh, "PICKING", null);
        f.transfer(wh, other, "DISPATCHED");
        f.adjustment(wh, f.product("adj", "1.00"), "PENDING_APPROVAL");
        String after = adminView(now).andReturn().getResponse().getContentAsString();
        for (String k : new String[]{"deliveriesPicking", "transfersInTransit", "adjustmentsPendingApproval"}) {
            int b = JsonPath.read(before, "$.operations." + k);
            int a = JsonPath.read(after, "$.operations." + k);
            assertThat(a - b).as(k).isEqualTo(1);
        }
    }

    @Test
    void noCostLeaksOutsideAdmin() throws Exception {
        adminView(q).andExpect(status().isOk());
        mvc.perform(get("/api/insights/admin").with(as(repA, Role.SALES))).andExpect(status().isForbidden());
        long wh = f.user("wh", Role.WAREHOUSE);
        mvc.perform(get("/api/insights/admin").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
