package ph.thecoffeejunkie.crm.insights;

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

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/** Every figure here is for fresh reps, so other tests' rows never count. */
@IntegrationTest
class SalesInsightsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    InsightsFixture f;
    long admin;
    long repA;
    long repB;
    long cust;
    final YearMonth m = YearMonth.now(InsightsMonth.MANILA);
    final LocalDate today = LocalDate.now(InsightsMonth.MANILA);

    @BeforeEach
    void seed() {
        caches.getCache("insights").invalidate();
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        repA = f.user("repa", Role.SALES);
        repB = f.user("repb", Role.SALES);
        cust = f.customer("acme", repA);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions sales(long rep, YearMonth month) throws Exception {
        return mvc.perform(get("/api/insights/sales").param("month", month.toString()).with(as(rep, Role.SALES)));
    }

    private long paidOrder(long rep) {
        return f.order(cust, rep, "FULFILLED", "100000.00", "0.00", null);
    }

    private void target(long rep, String amount, String pct) {
        jdbc.update("insert into sales_target (rep_id, month, target_amount, commission_pct, updated_by) "
                + "values (?, ?, ?::numeric, ?::numeric, ?)", rep, m.atDay(1), amount, pct, admin);
    }

    @Test
    void collectedCountsVerifiedOnlyByVerifiedDate() throws Exception {
        long o = paidOrder(repA);
        f.payment(o, "VERIFIED", "1000.00", "20.00", at(m, 30));
        f.payment(o, "PENDING", "500.00", "0.00", null);
        f.payment(o, "REJECTED", "300.00", "0.00", null);
        f.payment(o, "VERIFIED", "700.00", "0.00", at(m.minusMonths(1), 30));
        sales(repA, m).andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(m.toString()))
                .andExpect(jsonPath("$.collected").value(1020.00))
                .andExpect(jsonPath("$.toVerify.count").value(1))
                .andExpect(jsonPath("$.toVerify.amount").value(500.00));
    }

    @Test
    void lateVerifiedCountsInVerifyMonth() throws Exception {
        long o = paidOrder(repA);
        f.payment(o, "VERIFIED", "400.00", "0.00", m.minusMonths(1).atEndOfMonth(), at(m, 0).plusSeconds(1800));
        sales(repA, m).andExpect(jsonPath("$.collected").value(400.00));
        sales(repA, m.minusMonths(1)).andExpect(jsonPath("$.collected").value(0.00));
    }

    @Test
    void onlyOwnOrders() throws Exception {
        long mine = paidOrder(repA);
        f.payment(mine, "VERIFIED", "100.00", "0.00", at(m, 30));
        long other = f.customer("other", repB);
        long theirs = f.order(other, repB, "FULFILLED", "5000.00", "0.00", null);
        f.payment(theirs, "VERIFIED", "999.00", "0.00", at(m, 30));
        sales(repA, m).andExpect(jsonPath("$.collected").value(100.00))
                .andExpect(jsonPath("$.topCustomers.length()").value(1))
                .andExpect(jsonPath("$.topCustomers[0].name").value(f.tag + "acme"))
                .andExpect(jsonPath("$.topCustomers[0].collected").value(100.00));
    }

    @Test
    void reassignedAfterConfirmStaysWithRep() throws Exception {
        long o = paidOrder(repA);
        f.payment(o, "VERIFIED", "250.00", "0.00", at(m, 30));
        jdbc.update("update customer set assigned_rep_id = ? where id = ?", repB, cust);
        sales(repA, m).andExpect(jsonPath("$.collected").value(250.00));
        sales(repB, m).andExpect(jsonPath("$.collected").value(0.00));
    }

    /**
     * ₱12,000 of VATable goods less an ₱800 discount, plus ₱500 shipping: total 11,700, of which 10,000 is goods
     * net of VAT. Only that counts toward the target and commission; collected stays the cash.
     */
    @Test
    void targetAndCommissionExcludeVatShippingAndDiscount() throws Exception {
        long o = f.order(cust, repA, "FULFILLED", "11700.00", "0.00", null);
        jdbc.update("update sales_order set vat_exempt_sales = 0, vatable_sales = 10446.43, vat = 1253.57, "
                + "shipping_fee = 500, discount_amount = 800 where id = ?", o);
        target(repA, "10000.00", "5.00");
        f.payment(o, "VERIFIED", "5850.00", "0.00", at(m, 30));
        sales(repA, m).andExpect(jsonPath("$.collected").value(5850.00))
                .andExpect(jsonPath("$.netSales").value(5000.00))
                .andExpect(jsonPath("$.progressPct").value(50))
                .andExpect(jsonPath("$.targetHit").value(false));
        caches.getCache("insights").invalidate();
        f.payment(o, "VERIFIED", "5850.00", "0.00", at(m, 30));
        sales(repA, m).andExpect(jsonPath("$.collected").value(11700.00))
                .andExpect(jsonPath("$.netSales").value(10000.00))
                .andExpect(jsonPath("$.targetHit").value(true))
                .andExpect(jsonPath("$.commission").value(500.00));
    }

    @Test
    void invoicedExcludesVoid() throws Exception {
        long a = paidOrder(repA);
        long b = paidOrder(repA);
        f.invoice(a, "ISSUED", at(m, 30), "2000.00");
        f.invoice(b, "VOID", at(m, 30), "1500.00");
        sales(repA, m).andExpect(jsonPath("$.invoiced").value(2000.00));
    }

    @Test
    void outstandingAndOverdue() throws Exception {
        long late = f.order(cust, repA, "FULFILLED", "3000.00", "1000.00", today.minusDays(1));
        f.invoice(late, "ISSUED", at(m, 1), "3000.00");
        long notYet = f.order(cust, repA, "FULFILLED", "500.00", "0.00", today.plusDays(1));
        f.invoice(notYet, "ISSUED", at(m, 1), "500.00");
        long noInvoice = f.order(cust, repA, "CONFIRMED", "9000.00", "0.00", today.minusDays(5));
        assertThat(noInvoice).isPositive();
        sales(repA, m).andExpect(jsonPath("$.outstanding").value(2500.00))
                .andExpect(jsonPath("$.overdue").value(2000.00));
    }

    @Test
    void targetHitPaysCommission() throws Exception {
        f.payment(paidOrder(repA), "VERIFIED", "1000.00", "20.00", at(m, 30));
        target(repA, "1000.00", "5.00");
        sales(repA, m).andExpect(jsonPath("$.target.amount").value(1000.00))
                .andExpect(jsonPath("$.target.commissionPct").value(5.00))
                .andExpect(jsonPath("$.targetHit").value(true))
                .andExpect(jsonPath("$.commission").value(51.00))
                .andExpect(jsonPath("$.progressPct").value(102))
                .andExpect(jsonPath("$.remaining").value(0.00));
    }

    @Test
    void belowTargetNoCommission() throws Exception {
        f.payment(paidOrder(repA), "VERIFIED", "1000.00", "20.00", at(m, 30));
        target(repA, "5000.00", "5.00");
        sales(repA, m).andExpect(jsonPath("$.targetHit").value(false))
                .andExpect(jsonPath("$.commission").value(0.00))
                .andExpect(jsonPath("$.remaining").value(3980.00))
                .andExpect(jsonPath("$.progressPct").value(20));
    }

    @Test
    void noTargetMeansNullAndZeroCommission() throws Exception {
        f.payment(paidOrder(repA), "VERIFIED", "1000.00", "0.00", at(m, 30));
        sales(repA, m).andExpect(jsonPath("$.target").isEmpty())
                .andExpect(jsonPath("$.progressPct").isEmpty())
                .andExpect(jsonPath("$.remaining").isEmpty())
                .andExpect(jsonPath("$.targetHit").value(false))
                .andExpect(jsonPath("$.commission").value(0.00));
    }

    @Test
    void collectedByDayHasEveryDay() throws Exception {
        f.payment(paidOrder(repA), "VERIFIED", "300.00", "0.00", at(m, 30)); // day 2, 06:00 Manila
        sales(repA, m).andExpect(jsonPath("$.collectedByDay.length()").value(m.lengthOfMonth()))
                .andExpect(jsonPath("$.collectedByDay[0].date").value(m.atDay(1).toString()))
                .andExpect(jsonPath("$.collectedByDay[0].amount").value(0.00))
                .andExpect(jsonPath("$.collectedByDay[1].amount").value(300.00));
    }

    @Test
    void needsActionListsOldestFive() throws Exception {
        for (int i = 0; i < 6; i++) {
            long q = f.order(cust, repA, "QUOTED", "100.00", "0.00", null);
            jdbc.update("update sales_order set updated_at = now() - make_interval(days => ?) where id = ?", 10 - i, q);
        }
        long oldest = jdbc.queryForObject("select id from sales_order where rep_id = ? order by updated_at limit 1",
                Long.class, repA);
        long unpaid = f.order(cust, repA, "CONFIRMED", "800.00", "300.00", null);
        long overdue = f.order(cust, repA, "FULFILLED", "700.00", "0.00", today.minusDays(3));
        f.invoice(overdue, "ISSUED", at(m, 1), "700.00");
        sales(repA, m).andExpect(jsonPath("$.needsAction.openQuotes.length()").value(5))
                .andExpect(jsonPath("$.needsAction.openQuotes[0].orderId").value(oldest))
                .andExpect(jsonPath("$.needsAction.unpaidConfirmed[0].orderId").value(unpaid))
                .andExpect(jsonPath("$.needsAction.unpaidConfirmed[0].amount").value(500.00))
                .andExpect(jsonPath("$.needsAction.overdueInvoices[0].orderId").value(overdue))
                .andExpect(jsonPath("$.needsAction.overdueInvoices[0].since").value(today.minusDays(3).toString()));
    }

    @Test
    void needsActionFollowsCurrentCustomerRep() throws Exception {
        long unpaid = f.order(cust, repA, "CONFIRMED", "800.00", "300.00", null);
        jdbc.update("update customer set assigned_rep_id = ? where id = ?", repB, cust);
        sales(repA, m).andExpect(jsonPath("$.needsAction.unpaidConfirmed.length()").value(0));
        sales(repB, m).andExpect(jsonPath("$.needsAction.unpaidConfirmed[0].orderId").value(unpaid));
    }

    @Test
    void noCostFieldsInResponse() throws Exception {
        long o = paidOrder(repA);
        f.line(o, f.product("p", "60.00"), "1", "100.00", "60.00");
        String body = sales(repA, m).andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("cost").doesNotContain("margin");
    }

    @Test
    void targetChangeVisibleImmediately() throws Exception {
        sales(repA, m).andExpect(jsonPath("$.target").isEmpty());
        mvc.perform(f.json(put("/api/insights/targets/" + repA + "/" + m),
                "{\"targetAmount\":5000,\"commissionPct\":2,\"version\":null}").with(as(admin, Role.ADMIN)))
                .andExpect(status().isOk());
        sales(repA, m).andExpect(jsonPath("$.target.amount").value(5000.00));
    }

    @Test
    void badMonth400() throws Exception {
        sales(repA, m.plusMonths(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSIGHTS_BAD_MONTH"));
    }

    @Test
    void adminAndWarehouseForbidden() throws Exception {
        mvc.perform(get("/api/insights/sales").with(as(admin, Role.ADMIN))).andExpect(status().isForbidden());
        long wh = f.user("wh", Role.WAREHOUSE);
        mvc.perform(get("/api/insights/sales").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
