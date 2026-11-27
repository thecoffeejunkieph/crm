package ph.thecoffeejunkie.crm.insights;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/**
 * Ad performance cohorts (spec 2026-11-22). Everything sits in 2019, which no other test writes to, and source rows
 * are picked by this test's own source ids.
 */
@IntegrationTest
class AdPerformanceIT {

    static final YearMonth OCT = YearMonth.of(2019, 10);
    static final YearMonth NOV = YearMonth.of(2019, 11);
    static final YearMonth DEC = YearMonth.of(2019, 12);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    InsightsFixture f;
    long admin;
    RequestPostProcessor asAdmin;
    long fb;

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        asAdmin = as(admin, Role.ADMIN);
        fb = f.source("Facebook");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions report(String from, String to) throws Exception {
        return mvc.perform(get("/api/insights/ad-performance?from=" + from + "&to=" + to).with(asAdmin));
    }

    /** JSON path to a field of {@code sourceId}'s row in {@code month}. */
    private static String row(String month, long sourceId, String field) {
        return "$.months[?(@.month == '" + month + "')].rows[?(@.sourceId == " + sourceId + ")]." + field;
    }

    private void spend(long sourceId, YearMonth m, String amount) {
        jdbc.update("insert into ad_spend (source_id, month, amount, updated_by) values (?, ?, ?::numeric, ?)",
                sourceId, m.atDay(1), amount, admin);
    }

    private long customerPaying(String name, long sourceId, Long leadId, Instant created, String paid) {
        long c = f.customer(name, sourceId, leadId, created);
        if (paid != null) {
            long o = f.order(c, null, "CONFIRMED", paid, paid, null);
            f.payment(o, "VERIFIED", paid, "0", created);
        }
        return c;
    }

    @Test
    void leadMonthWins() throws Exception {
        long lead = f.lead("ana", fb, at(OCT, 24));
        customerPaying("ana", fb, lead, at(DEC, 24), "5000");
        report("2019-10", "2019-12").andExpect(status().isOk())
                .andExpect(jsonPath(row("2019-10", fb, "leads"), contains(1)))
                .andExpect(jsonPath(row("2019-10", fb, "newCustomers"), contains(1)))
                .andExpect(jsonPath(row("2019-10", fb, "cohortValue"), contains(5000.00)))
                .andExpect(jsonPath(row("2019-12", fb, "newCustomers"), empty()));
    }

    @Test
    void directCustomerUsesCreatedMonth() throws Exception {
        customerPaying("ben", fb, null, at(NOV, 24), "100");
        report("2019-11", "2019-11").andExpect(jsonPath(row("2019-11", fb, "newCustomers"), contains(1)));
    }

    @Test
    void refundsReduceValue() throws Exception {
        long c = f.customer("cy", fb, null, at(NOV, 24));
        long o = f.order(c, null, "CONFIRMED", "5000", "5000", null);
        f.payment(o, "VERIFIED", "5000", "0", at(NOV, 30));
        f.refund(o, "1000", at(NOV, 40));
        report("2019-11", "2019-11").andExpect(jsonPath(row("2019-11", fb, "cohortValue"), contains(4000.00)));
    }

    @Test
    void ratios() throws Exception {
        spend(fb, NOV, "10000");
        customerPaying("d1", fb, null, at(NOV, 24), "6000");
        customerPaying("d2", fb, null, at(NOV, 48), "9000");
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "costPerCustomer"), contains(5000.00)))
                .andExpect(jsonPath(row("2019-11", fb, "valuePerCustomer"), contains(7500.00)))
                .andExpect(jsonPath(row("2019-11", fb, "roas"), contains(1.50)));
    }

    @Test
    void nullsWhenNoSpend() throws Exception {
        customerPaying("e", fb, null, at(NOV, 24), "100");
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "spend"), contains(nullValue())))
                .andExpect(jsonPath(row("2019-11", fb, "costPerCustomer"), contains(nullValue())))
                .andExpect(jsonPath(row("2019-11", fb, "roas"), contains(nullValue())));
    }

    @Test
    void zeroSpendGivesNullRoasAndZeroCost() throws Exception {
        spend(fb, NOV, "0");
        customerPaying("g", fb, null, at(NOV, 24), "100");
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "roas"), contains(nullValue())))
                .andExpect(jsonPath(row("2019-11", fb, "costPerCustomer"), contains(0.00)));
    }

    @Test
    void leadsWithoutCustomersGiveNullPerCustomer() throws Exception {
        spend(fb, NOV, "1000");
        f.lead("h", fb, at(NOV, 24));
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "leads"), contains(1)))
                .andExpect(jsonPath(row("2019-11", fb, "costPerCustomer"), contains(nullValue())))
                .andExpect(jsonPath(row("2019-11", fb, "valuePerCustomer"), contains(nullValue())));
    }

    @Test
    void cohortMonthUsesManilaTime() throws Exception {
        long lead = f.lead("i", fb, Instant.parse("2019-10-31T16:30:00Z"));
        customerPaying("i", fb, lead, at(DEC, 24), null);
        report("2019-10", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "newCustomers"), contains(1)))
                .andExpect(jsonPath(row("2019-10", fb, "newCustomers"), empty()));
    }

    @Test
    void usesCustomersCurrentSource() throws Exception {
        long other = f.source("Referral");
        long lead = f.lead("j", fb, at(NOV, 24));
        customerPaying("j", other, lead, at(NOV, 48), null);
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", other, "newCustomers"), contains(1)))
                .andExpect(jsonPath(row("2019-11", fb, "leads"), contains(1)))
                .andExpect(jsonPath(row("2019-11", fb, "newCustomers"), contains(0)));
    }

    @Test
    void spendOnlyRowAppears() throws Exception {
        spend(fb, NOV, "3000");
        report("2019-11", "2019-11")
                .andExpect(jsonPath(row("2019-11", fb, "newCustomers"), contains(0)))
                .andExpect(jsonPath(row("2019-11", fb, "cohortValue"), contains(0.00)))
                .andExpect(jsonPath(row("2019-11", fb, "roas"), contains(0.00)));
    }

    @Test
    void monthsOrderedAcrossYear() throws Exception {
        report("2019-11", "2020-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.months[*].month", contains("2019-11", "2019-12", "2020-01")));
    }

    @Test
    void totalRecomputesRatios() throws Exception {
        long other = f.source("Trade show");
        spend(fb, NOV, "1000");
        spend(other, NOV, "3000");
        customerPaying("k1", fb, null, at(NOV, 24), "2000");
        customerPaying("k2", other, null, at(NOV, 24), "6000");
        report("2019-11", "2019-11")
                .andExpect(jsonPath("$.months[0].total.spend").value(4000.00))
                .andExpect(jsonPath("$.months[0].total.newCustomers").value(2))
                .andExpect(jsonPath("$.months[0].total.cohortValue").value(8000.00))
                .andExpect(jsonPath("$.months[0].total.roas").value(2.00))
                .andExpect(jsonPath("$.months[0].total.sourceId").value(nullValue()));
    }

    @Test
    void totalSpendNullWithoutAnySpend() throws Exception {
        customerPaying("l", fb, null, at(NOV, 24), "100");
        report("2019-11", "2019-11")
                .andExpect(jsonPath("$.months[0].total.spend").value(nullValue()))
                .andExpect(jsonPath("$.months[0].total.roas").value(nullValue()));
    }

    @Test
    void badRange400() throws Exception {
        report("2019-12", "2019-11").andExpect(status().isBadRequest());
        report("2018-01", "2020-01").andExpect(status().isBadRequest());
        report("2018-02", "2020-01").andExpect(status().isOk());
    }

    @Test
    void salesForbidden() throws Exception {
        long rep = f.user("rep", Role.SALES);
        mvc.perform(get("/api/insights/ad-performance").with(as(rep, Role.SALES))).andExpect(status().isForbidden());
    }
}
