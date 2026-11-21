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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;

/** Monthly ad spend per source (spec 2026-11-22-ad-performance). */
@IntegrationTest
class AdSpendIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    InsightsFixture f;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asSales;
    long src;

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        asAdmin = as(f.user("adm", Role.ADMIN), Role.ADMIN);
        asSales = as(f.user("rep", Role.SALES), Role.SALES);
        src = f.source("Facebook");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions putSpend(RequestPostProcessor who, long sourceId, String month, String body) throws Exception {
        return mvc.perform(f.json(put("/api/insights/ad-spend/" + sourceId + "/" + month), body).with(who));
    }

    private ResultActions clear(RequestPostProcessor who, long sourceId, String month) throws Exception {
        return mvc.perform(delete("/api/insights/ad-spend/" + sourceId + "/" + month).with(csrf(mvc)).with(who));
    }

    private ResultActions list(RequestPostProcessor who, String month) throws Exception {
        return mvc.perform(get("/api/insights/ad-spend?month=" + month).with(who));
    }

    private String row(long id) {
        return "$[?(@.sourceId == " + id + ")]";
    }

    @Test
    void putThenGetShowsAmount() throws Exception {
        putSpend(asAdmin, src, "2026-11", "{\"amount\": 15000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(15000.00))
                .andExpect(jsonPath("$.sourceName").value(f.tag + "Facebook"));
        list(asAdmin, "2026-11").andExpect(status().isOk())
                .andExpect(jsonPath(row(src) + ".amount", contains(15000.00)));
    }

    @Test
    void putOverwrites() throws Exception {
        putSpend(asAdmin, src, "2026-11", "{\"amount\": 15000}").andExpect(status().isOk());
        putSpend(asAdmin, src, "2026-11", "{\"amount\": 9000}").andExpect(status().isOk());
        list(asAdmin, "2026-11").andExpect(jsonPath(row(src) + ".amount", contains(9000.00)));
        assertEquals(1, jdbc.queryForObject("select count(*) from ad_spend where source_id = ? and month = '2026-11-01'",
                Integer.class, src));
    }

    @Test
    void deleteClears() throws Exception {
        putSpend(asAdmin, src, "2026-11", "{\"amount\": 15000}").andExpect(status().isOk());
        clear(asAdmin, src, "2026-11").andExpect(status().isNoContent());
        list(asAdmin, "2026-11").andExpect(jsonPath(row(src) + ".amount", contains(nullValue())));
        clear(asAdmin, src, "2026-11").andExpect(status().isNoContent());
    }

    @Test
    void archivedSourceShownOnlyWithSpend() throws Exception {
        long archived = f.source("Old");
        putSpend(asAdmin, archived, "2026-11", "{\"amount\": 500}").andExpect(status().isOk());
        jdbc.update("update lead_source set archived_at = now() where id = ?", archived);
        list(asAdmin, "2026-11").andExpect(jsonPath(row(archived) + ".archived", contains(true)));
        list(asAdmin, "2026-10").andExpect(jsonPath(row(archived), empty()));
    }

    @Test
    void negativeAmountRejected() throws Exception {
        putSpend(asAdmin, src, "2026-11", "{\"amount\": -1}").andExpect(status().isBadRequest());
    }

    @Test
    void unknownSource404() throws Exception {
        putSpend(asAdmin, 999999999L, "2026-11", "{\"amount\": 1}").andExpect(status().isNotFound());
    }

    @Test
    void salesForbidden() throws Exception {
        list(asSales, "2026-11").andExpect(status().isForbidden());
        putSpend(asSales, src, "2026-11", "{\"amount\": 1}").andExpect(status().isForbidden());
        clear(asSales, src, "2026-11").andExpect(status().isForbidden());
    }
}
