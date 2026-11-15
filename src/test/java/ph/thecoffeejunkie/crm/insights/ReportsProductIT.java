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

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.at;

/** By-product report: each payment split over the order's lines pro rata (spec 2026-11-07). Ranges sit in 2021. */
@IntegrationTest
class ReportsProductIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    InsightsFixture f;
    long rep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        asAdmin = as(f.user("adm", Role.ADMIN), Role.ADMIN);
        rep = f.user("rep", Role.SALES);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions products(RequestPostProcessor who, YearMonth m) throws Exception {
        return mvc.perform(get("/api/reports/sales?by=product&from=" + m.atDay(1) + "&to=" + m.atEndOfMonth()).with(who));
    }

    @Test
    void splitsTwoPaymentsAcrossLinesAndShipping() throws Exception {
        YearMonth jan = YearMonth.of(2021, 1);
        long p1 = f.product("P1", "100");
        long p2 = f.product("P2", "50");
        long o = f.order(f.customer("Acme", rep), rep, "CONFIRMED", "1100", "1100", null);
        f.set(o, "shipping_fee", new BigDecimal("100"));
        f.line(o, p1, "3", "600", "100");
        f.line(o, p2, "1", "400", null);
        f.payment(o, "VERIFIED", "550", "0", at(jan, 1));
        f.payment(o, "VERIFIED", "500", "50", at(jan, 2));
        products(asAdmin, jan).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].name", contains(f.tag + "P1", f.tag + "P2", "Shipping")))
                .andExpect(jsonPath("$.rows[0].id").value(p1))
                .andExpect(jsonPath("$.rows[0].collected").value(600.0))
                .andExpect(jsonPath("$.rows[0].margin").value(300.0))
                .andExpect(jsonPath("$.rows[0].marginPct").value(50.0))
                .andExpect(jsonPath("$.rows[0].linesWithoutCost").value(0))
                .andExpect(jsonPath("$.rows[1].collected").value(400.0))
                .andExpect(jsonPath("$.rows[1].linesWithoutCost").value(1))
                .andExpect(jsonPath("$.rows[1].margin").isEmpty())
                .andExpect(jsonPath("$.rows[2].id").isEmpty())
                .andExpect(jsonPath("$.rows[2].collected").value(100.0))
                .andExpect(jsonPath("$.total.collected").value(1100.0))
                .andExpect(jsonPath("$.total.payments").value(2));
    }

    @Test
    void partialPaymentIsProRata() throws Exception {
        YearMonth feb = YearMonth.of(2021, 2);
        long a = f.product("A", "10");
        long b = f.product("B", "10");
        long o = f.order(f.customer("Part", rep), rep, "CONFIRMED", "1000", "400", null);
        f.line(o, a, "1", "750", "10");
        f.line(o, b, "1", "250", "10");
        f.payment(o, "VERIFIED", "400", "0", at(feb, 3));
        products(asAdmin, feb)
                .andExpect(jsonPath("$.rows[*].collected", contains(300.0, 100.0)));
    }

    @Test
    void zeroTotalOrderIsSkipped() throws Exception {
        YearMonth mar = YearMonth.of(2021, 3);
        long o = f.order(f.customer("Free", rep), rep, "CONFIRMED", "0", "0", null);
        f.line(o, f.product("Gift", "5"), "1", "0", "5");
        f.payment(o, "VERIFIED", "10", "0", at(mar, 1)); // payments must be > 0; the order total is what is 0
        products(asAdmin, mar).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty());
    }

    @Test
    void salesGetsNoMargin() throws Exception {
        YearMonth apr = YearMonth.of(2021, 4);
        long o = f.order(f.customer("Own", rep), rep, "CONFIRMED", "200", "200", null);
        f.line(o, f.product("Bean", "50"), "2", "200", "50");
        f.payment(o, "VERIFIED", "200", "0", at(apr, 1));
        products(as(rep, Role.SALES), apr).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].collected").value(200.0))
                .andExpect(jsonPath("$.rows[0].margin").isEmpty())
                .andExpect(jsonPath("$.rows[0].marginPct").isEmpty())
                .andExpect(jsonPath("$.rows[0].linesWithoutCost").isEmpty());
    }
}
