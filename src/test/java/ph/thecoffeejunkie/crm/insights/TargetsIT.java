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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;

@IntegrationTest
class TargetsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    InsightsFixture f;
    long admin;
    long repA;
    long repB;
    RequestPostProcessor asAdmin;
    final YearMonth now = YearMonth.now(ZoneId.of("Asia/Manila"));
    final YearMonth prev = now.minusMonths(1);

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

    private ResultActions putTarget(long rep, YearMonth m, String amount, String pct, Long version) throws Exception {
        return mvc.perform(f.json(put("/api/insights/targets/" + rep + "/" + m),
                "{\"targetAmount\":" + amount + ",\"commissionPct\":" + pct + ",\"version\":" + version + "}")
                .with(asAdmin));
    }

    private static String row(long rep) {
        return "$[?(@.repId == " + rep + ")]";
    }

    @Test
    void listShowsEverySalesRepWithNulls() throws Exception {
        mvc.perform(get("/api/insights/targets").param("month", now.toString()).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row(repA) + ".repName").value(contains(f.tag + "repa U")))
                .andExpect(jsonPath(row(repA) + ".targetAmount").value(contains((Object) null)))
                .andExpect(jsonPath(row(repB)).isNotEmpty())
                .andExpect(jsonPath(row(admin)).isEmpty());
    }

    @Test
    void putCreatesThenUpdatesWithVersion() throws Exception {
        String body = putTarget(repA, now, "50000.00", "3.00", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(50000.00))
                .andExpect(jsonPath("$.commissionPct").value(3.00))
                .andReturn().getResponse().getContentAsString();
        long v = ((Number) JsonPath.read(body, "$.version")).longValue();
        putTarget(repA, now, "60000.00", "4.00", v).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(60000.00));
        putTarget(repA, now, "70000.00", "4.00", v).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        putTarget(repA, now, "70000.00", "4.00", null).andExpect(status().isConflict());
    }

    @Test
    void putForNonRep422() throws Exception {
        long wh = f.user("wh", Role.WAREHOUSE);
        putTarget(wh, now, "1000", "1", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSIGHTS_NOT_A_REP"));
    }

    @Test
    void putRejectsPctOver100() throws Exception {
        putTarget(repA, now, "1000", "100.01", null).andExpect(status().isBadRequest());
        putTarget(repA, now, "-1", "5", null).andExpect(status().isBadRequest());
    }

    @Test
    void copyFillsOnlyEmptyRows() throws Exception {
        putTarget(repA, prev, "1000", "1", null).andExpect(status().isOk());
        putTarget(repB, prev, "2000", "2", null).andExpect(status().isOk());
        putTarget(repA, now, "9999", "9", null).andExpect(status().isOk());
        mvc.perform(post("/api/insights/targets/copy").param("from", prev.toString()).param("to", now.toString())
                        .with(ph.thecoffeejunkie.crm.CsrfSupport.csrf(mvc)).with(asAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.copied").value(1));
        mvc.perform(get("/api/insights/targets").param("month", now.toString()).with(asAdmin))
                .andExpect(jsonPath(row(repA) + ".targetAmount").value(contains(9999.0)))
                .andExpect(jsonPath(row(repB) + ".targetAmount").value(contains(2000.0)))
                .andExpect(jsonPath(row(repB) + ".commissionPct").value(contains(2.0)));
    }

    @Test
    void putWritesAudit() throws Exception {
        putTarget(repA, now, "1000", "1", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'SALES_TARGET_SET' "
                + "and actor_id = ?", Integer.class, admin)).isEqualTo(1);
    }

    @Test
    void badMonth400() throws Exception {
        mvc.perform(get("/api/insights/targets").param("month", "2026-13").with(asAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSIGHTS_BAD_MONTH"));
    }

    @Test
    void salesForbidden() throws Exception {
        mvc.perform(get("/api/insights/targets").with(as(repA, Role.SALES))).andExpect(status().isForbidden());
    }

    @Test
    void savingTargetEvictsCache() throws Exception {
        caches.getCache("insights").put("probe", "x");
        putTarget(repA, now, "1000", "1", null).andExpect(status().isOk());
        assertThat(caches.getCache("insights").get("probe")).isNull();
    }
}
