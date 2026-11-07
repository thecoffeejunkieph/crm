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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/** Reports (spec 2026-11-07). Ranges sit in 2021, which no other test writes to, so company totals are ours. */
@IntegrationTest
class ReportsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    InsightsFixture f;
    long admin;
    long repA;
    long repB;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        repA = f.user("repa", Role.SALES);
        repB = f.user("repb", Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions report(RequestPostProcessor who, String query) throws Exception {
        return mvc.perform(get("/api/reports/sales?" + query).with(who));
    }

    private long paidOrder(Long rep, String customer, String amount, Instant verified) {
        long o = f.order(f.customer(customer, rep), rep, "CONFIRMED", amount, amount, null);
        f.payment(o, "VERIFIED", amount, "0", verified);
        return o;
    }

    @Test
    void onlyVerifiedCountsByVerifiedDate() throws Exception {
        YearMonth mar = YearMonth.of(2021, 3);
        long o = f.order(f.customer("Acme", repA), repA, "CONFIRMED", "5000", "0", null);
        f.payment(o, "VERIFIED", "1000", "200", at(mar, 30));
        f.payment(o, "PENDING", "500", "0", null);
        f.payment(o, "REJECTED", "700", "0", at(mar, 40));
        f.payment(o, "VERIFIED", "300", "0", java.time.LocalDate.of(2021, 3, 31), at(YearMonth.of(2021, 4), 2));
        report(asAdmin, "by=month&from=2021-03-01&to=2021-04-30").andExpect(status().isOk())
                .andExpect(jsonPath("$.by").value("MONTH"))
                .andExpect(jsonPath("$.rows[*].month", contains("2021-03", "2021-04")))
                .andExpect(jsonPath("$.rows[0].collected").value(1200.0))
                .andExpect(jsonPath("$.rows[0].payments").value(1))
                .andExpect(jsonPath("$.rows[1].collected").value(300.0))
                .andExpect(jsonPath("$.total.collected").value(1500.0))
                .andExpect(jsonPath("$.total.payments").value(2));
    }

    @Test
    void rangeEndIsInclusiveInManila() throws Exception {
        paidOrder(repA, "Late", "100", Instant.parse("2021-05-31T15:30:00Z"));
        paidOrder(repA, "Next", "50", Instant.parse("2021-05-31T16:30:00Z"));
        report(asAdmin, "by=month&from=2021-05-01&to=2021-05-31")
                .andExpect(jsonPath("$.total.collected").value(100.0))
                .andExpect(jsonPath("$.total.payments").value(1));
    }

    @Test
    void zeroMonthsIncluded() throws Exception {
        paidOrder(repA, "Jul", "250", at(YearMonth.of(2021, 7), 10));
        report(asAdmin, "by=month&from=2021-06-01&to=2021-08-31")
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[0].collected").value(0.0))
                .andExpect(jsonPath("$.rows[0].payments").value(0))
                .andExpect(jsonPath("$.rows[1].collected").value(250.0))
                .andExpect(jsonPath("$.rows[2].collected").value(0.0));
    }

    @Test
    void repRows() throws Exception {
        YearMonth sep = YearMonth.of(2021, 9);
        long repC = f.user("repc", Role.SALES);
        paidOrder(repA, "Own", "600", at(sep, 5));
        long moved = paidOrder(repA, "Moved", "400", at(sep, 6));
        jdbc.update("update customer set assigned_rep_id = ? where id = (select customer_id from sales_order where id = ?)",
                repB, moved);
        paidOrder(repC, "Gone", "400", at(sep, 7));
        jdbc.update("update app_user set archived_at = now() where id = ?", repC);
        paidOrder(null, "Loose", "100", at(sep, 8));
        String q = "by=rep&from=2021-09-01&to=2021-09-30";
        report(asAdmin, q).andExpect(status().isOk())
                .andExpect(jsonPath("$.total.collected").value(1500.0))
                .andExpect(jsonPath("$.rows[0].id").value(repA))
                .andExpect(jsonPath("$.rows[0].collected").value(1000.0))
                .andExpect(jsonPath("$.rows[0].sharePct").value(66.7))
                .andExpect(jsonPath("$.rows[1].id").value(repC))
                .andExpect(jsonPath("$.rows[1].name").value(f.tag + "repc U"))
                .andExpect(jsonPath("$.rows[2].name").value("Unassigned"))
                .andExpect(jsonPath("$.rows[2].id").isEmpty())
                .andExpect(jsonPath("$.rows[2].collected").value(100.0))
                .andExpect(jsonPath("$.rows[?(@.id==" + repB + ")].collected").value(0.0));
    }

    @Test
    void customerRowsAndPaymentCount() throws Exception {
        YearMonth oct = YearMonth.of(2021, 10);
        long o = f.order(f.customer("Bravo", repA), repA, "CONFIRMED", "1000", "1000", null);
        f.payment(o, "VERIFIED", "600", "0", at(oct, 1));
        f.payment(o, "VERIFIED", "400", "0", at(oct, 2));
        paidOrder(repA, "Alpha", "1000", at(oct, 3));
        report(asAdmin, "by=customer&from=2021-10-01&to=2021-10-31")
                .andExpect(jsonPath("$.rows[*].name", contains(f.tag + "Alpha", f.tag + "Bravo")))
                .andExpect(jsonPath("$.rows[0].payments").value(1))
                .andExpect(jsonPath("$.rows[1].payments").value(2))
                .andExpect(jsonPath("$.rows[1].sharePct").value(50.0))
                .andExpect(jsonPath("$.total.payments").value(3));
    }

    @Test
    void unassignedFilter() throws Exception {
        YearMonth nov = YearMonth.of(2021, 11);
        paidOrder(repA, "Mine", "700", at(nov, 1));
        paidOrder(null, "Nobody", "90", at(nov, 2));
        report(asAdmin, "by=customer&from=2021-11-01&to=2021-11-30&repId=0")
                .andExpect(jsonPath("$.rows[*].name", contains(f.tag + "Nobody")))
                .andExpect(jsonPath("$.total.collected").value(90.0));
        report(asAdmin, "by=customer&from=2021-11-01&to=2021-11-30&repId=" + repA)
                .andExpect(jsonPath("$.rows[*].name", contains(f.tag + "Mine")));
    }

    @Test
    void salesSeesOwnOnlyAndNoRepReport() throws Exception {
        YearMonth dec = YearMonth.of(2021, 12);
        paidOrder(repA, "Ours", "300", at(dec, 1));
        paidOrder(repB, "Theirs", "800", at(dec, 2));
        RequestPostProcessor asA = as(repA, Role.SALES);
        String range = "&from=2021-12-01&to=2021-12-31";
        report(asA, "by=customer" + range).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].name", contains(f.tag + "Ours")))
                .andExpect(jsonPath("$.rows[0].margin").isEmpty())
                .andExpect(jsonPath("$.total.collected").value(300.0));
        report(asA, "by=rep" + range).andExpect(status().isForbidden());
        report(asA, "by=customer&repId=" + repA + range).andExpect(status().isForbidden());
    }

    @Test
    void badRangeAndWarehouse() throws Exception {
        report(asAdmin, "from=2021-02-01&to=2021-01-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_BAD_RANGE"));
        report(asAdmin, "by=x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_BAD_RANGE"));
        report(as(f.user("wh", Role.WAREHOUSE), Role.WAREHOUSE), "by=month").andExpect(status().isForbidden());
    }
}
