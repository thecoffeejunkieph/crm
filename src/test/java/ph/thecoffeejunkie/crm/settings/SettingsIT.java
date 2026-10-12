package ph.thecoffeejunkie.crm.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SettingsIT {

    @Autowired MockMvc mvc;
    @Autowired Settings settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager caches;

    @AfterEach
    void restoreSeeds() {
        jdbc.update("update app_setting set value = '15' where key = 'approval.discountPercentMax'");
        jdbc.update("update app_setting set value = '10' where key = 'approval.writeOffQtyMax'");
        jdbc.update("update app_setting set value = '\"SELF\"' where key = 'pos.quickAddAssignment'");
        caches.getCache("settings").clear();
    }

    private static UsernamePasswordAuthenticationToken auth(Role role) {
        var p = new StaffPrincipal(1L, "a@x.ph", "Ann Admin", role, Set.of());
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MockHttpServletRequestBuilder putJson(String body) {
        return put("/api/admin/settings").with(authentication(auth(Role.ADMIN))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void seededDefaultsReadable() throws Exception {
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo("15");
        assertThat(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_QTY_MAX)).isEqualByComparingTo("10");
        assertThat(settings.text(SettingKey.POS_QUICK_ADD_ASSIGNMENT)).isEqualTo("SELF");
        mvc.perform(get("/api/admin/settings").with(authentication(auth(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['approval.discountPercentMax']").value(15))
                .andExpect(jsonPath("$['pos.quickAddAssignment']").value("SELF"));
    }

    @Test
    void updateIsAudited() throws Exception {
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo("15"); // warm cache
        int before = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class);
        mvc.perform(putJson("{\"approval.discountPercentMax\": 20, \"pos.quickAddAssignment\": \"UNASSIGNED\"}"))
                .andExpect(status().isOk());
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(settings.text(SettingKey.POS_QUICK_ADD_ASSIGNMENT)).isEqualTo("UNASSIGNED");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class))
                .isEqualTo(before + 2);
        assertThat(jdbc.queryForObject("select changes::text from audit_event where summary like '%approval.discountPercentMax%' order by id desc limit 1", String.class))
                .contains("15").contains("20");
    }

    @Test
    void unchangedValuesNotAudited() throws Exception {
        int before = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class);
        mvc.perform(putJson("{\"approval.discountPercentMax\": 15, \"approval.writeOffQtyMax\": 10.0,"
                + " \"pos.quickAddAssignment\": \"SELF\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void negativeThresholdRejected() throws Exception {
        mvc.perform(putJson("{\"approval.discountPercentMax\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo("15");
    }

    @Test
    void invalidValueWritesNothing() throws Exception {
        mvc.perform(putJson("{\"approval.writeOffQtyMax\": 5, \"approval.discountPercentMax\": 101}"))
                .andExpect(status().isBadRequest());
        assertThat(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_QTY_MAX)).isEqualByComparingTo("10");
    }

    @Test
    void nonNumericRejected() throws Exception {
        mvc.perform(putJson("{\"approval.discountPercentMax\": \"abc\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(putJson("{\"pos.quickAddAssignment\": \"OTHER\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownKeyRejected() throws Exception {
        mvc.perform(putJson("{\"nope\": 1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void salesCannotRead() throws Exception {
        mvc.perform(get("/api/admin/settings").with(authentication(auth(Role.SALES))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/settings").with(authentication(auth(Role.SALES))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
}
