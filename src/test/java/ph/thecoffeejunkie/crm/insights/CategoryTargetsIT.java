package ph.thecoffeejunkie.crm.insights;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.CsrfSupport;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.insights.InsightsFixture.as;

@IntegrationTest
class CategoryTargetsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    InsightsFixture f;
    long admin;
    long rep;
    long coffee;
    long tea;
    RequestPostProcessor asAdmin;
    final YearMonth now = YearMonth.now(ZoneId.of("Asia/Manila"));

    @BeforeEach
    void seed() {
        f = new InsightsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        coffee = f.category("coffee");
        tea = f.category("tea");
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private String url(long repId, long catId) {
        return "/api/insights/targets/" + repId + "/" + now + "/categories/" + catId;
    }

    private ResultActions putCat(long repId, YearMonth m, long catId, String amount, Long version) throws Exception {
        return mvc.perform(f.json(put("/api/insights/targets/" + repId + "/" + m + "/categories/" + catId),
                "{\"targetAmount\":" + amount + ",\"version\":" + version + "}").with(asAdmin));
    }

    private ResultActions list() throws Exception {
        return mvc.perform(get("/api/insights/targets/" + rep + "/" + now + "/categories").with(asAdmin));
    }

    private static String row(long cat) {
        return "$[?(@.categoryId == " + cat + ")]";
    }

    @Test
    void listShowsActiveCategoriesWithNulls() throws Exception {
        long old = f.category("old");
        f.archiveCategory(old);
        list().andExpect(status().isOk())
                .andExpect(jsonPath(row(coffee) + ".categoryName").value(contains(f.tag + "coffee")))
                .andExpect(jsonPath(row(coffee) + ".targetAmount").value(contains((Object) null)))
                .andExpect(jsonPath(row(coffee) + ".version").value(contains((Object) null)))
                .andExpect(jsonPath(row(coffee) + ".archived").value(contains(false)))
                .andExpect(jsonPath(row(old)).isEmpty());
    }

    @Test
    void putCreatesThenUpdatesWithVersion() throws Exception {
        putCat(rep, now, coffee, "30000.00", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetAmount").value(30000.00))
                .andExpect(jsonPath("$.version").value(0));
        putCat(rep, now, coffee, "35000.00", 0L).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        list().andExpect(jsonPath(row(coffee) + ".targetAmount").value(contains(35000.0)));
    }

    @Test
    void staleOrMissingVersionIs409() throws Exception {
        putCat(rep, now, coffee, "100", 0L).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        putCat(rep, now, coffee, "100", null).andExpect(status().isOk());
        putCat(rep, now, coffee, "200", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        putCat(rep, now, coffee, "200", 7L).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void zeroOrNegativeAmountIs400() throws Exception {
        putCat(rep, now, coffee, "0", null).andExpect(status().isBadRequest());
        putCat(rep, now, coffee, "-1", null).andExpect(status().isBadRequest());
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        putCat(rep, now, Long.MAX_VALUE, "100", null).andExpect(status().isNotFound());
    }

    @Test
    void archivedCategoryIs422() throws Exception {
        f.archiveCategory(tea);
        putCat(rep, now, tea, "100", null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void nonRepIs422() throws Exception {
        putCat(admin, now, coffee, "100", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSIGHTS_NOT_A_REP"));
    }

    @Test
    void archivedCategoryWithTargetStaysListedAndClearable() throws Exception {
        putCat(rep, now, tea, "500", null).andExpect(status().isOk());
        f.archiveCategory(tea);
        list().andExpect(jsonPath(row(tea) + ".archived").value(contains(true)))
                .andExpect(jsonPath(row(tea) + ".targetAmount").value(contains(500.0)));
        putCat(rep, now, tea, "600", 0L).andExpect(status().isUnprocessableContent());
        mvc.perform(delete(url(rep, tea)).param("version", "0").with(CsrfSupport.csrf(mvc)).with(asAdmin))
                .andExpect(status().isNoContent());
        list().andExpect(jsonPath(row(tea)).isEmpty());
    }

    @Test
    void deleteStaleIs409AndMissingIs404() throws Exception {
        mvc.perform(delete(url(rep, coffee)).param("version", "0").with(CsrfSupport.csrf(mvc)).with(asAdmin))
                .andExpect(status().isNotFound());
        putCat(rep, now, coffee, "100", null).andExpect(status().isOk());
        mvc.perform(delete(url(rep, coffee)).param("version", "5").with(CsrfSupport.csrf(mvc)).with(asAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void salesUserIs403() throws Exception {
        mvc.perform(get("/api/insights/targets/" + rep + "/" + now + "/categories").with(as(rep, Role.SALES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void copyAlsoCopiesCategoryTargets() throws Exception {
        YearMonth prev = now.minusMonths(1);
        long archivedCat = f.category("gone");
        long inactiveRep = f.user("inact", Role.SALES);
        putCat(rep, prev, coffee, "500.00", null).andExpect(status().isOk());
        putCat(rep, prev, tea, "700.00", null).andExpect(status().isOk());
        putCat(rep, prev, archivedCat, "100.00", null).andExpect(status().isOk());
        putCat(inactiveRep, prev, coffee, "900.00", null).andExpect(status().isOk());
        f.archiveCategory(archivedCat);
        jdbc.update("update app_user set archived_at = now() where id = ?", inactiveRep);
        putCat(rep, now, tea, "50.00", null).andExpect(status().isOk());

        mvc.perform(post("/api/insights/targets/copy").param("from", prev.toString()).param("to", now.toString())
                        .with(CsrfSupport.csrf(mvc)).with(asAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriesCopied").value(1));

        list().andExpect(jsonPath(row(coffee) + ".targetAmount").value(contains(500.0)))
                .andExpect(jsonPath(row(tea) + ".targetAmount").value(contains(50.0)));
        assertThat(jdbc.queryForObject("select count(*) from sales_category_target where month = ? and "
                + "(category_id = ? or rep_id = ?)", Integer.class, java.sql.Date.valueOf(now.atDay(1)), archivedCat,
                inactiveRep)).isZero();
    }

    @Test
    void writeIsAudited() throws Exception {
        String body = putCat(rep, now, coffee, "30000.00", null).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.version")).longValue()).isZero();
        String summary = jdbc.queryForObject("select summary from audit_event where entity_type = "
                + "'SALES_CATEGORY_TARGET' order by id desc limit 1", String.class);
        assertThat(summary).isEqualTo("Set " + f.tag + "rep U's " + f.tag + "coffee target for "
                + now.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)) + " to 30000.00");
    }
}
