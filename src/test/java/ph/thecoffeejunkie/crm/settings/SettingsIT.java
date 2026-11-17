package ph.thecoffeejunkie.crm.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
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

    // app_setting.updated_by is a FK, so the acting user must exist.
    long adminId;

    @BeforeEach
    void createActor() {
        adminId = jdbc.queryForObject("with u as (insert into app_user (email, first_name, last_name) "
                + "values ('actor@settings.it', 'Ann', 'Admin') returning id) insert into user_role select id, 'ADMIN' from u returning user_id", Long.class);
    }

    @AfterEach
    void restoreSeeds() {
        jdbc.update("delete from app_setting where key like 'followUps.%'");
        jdbc.update("update app_setting set updated_by = null");
        jdbc.update("delete from app_user where id = ?", adminId);
        jdbc.update("update app_setting set value = '15' where key = 'approval.discountPercentMax'");
        jdbc.update("update app_setting set value = '5000' where key = 'approval.writeOffValueMax'");
        jdbc.update("update app_setting set value = '[]' where key = 'invoice.bankAccounts'");
        jdbc.update("update app_setting set value = '\"\"' where key = 'invoice.terms'");
        jdbc.update("update app_setting set value = '15' where key = 'sales.quoteValidityDays'");
        jdbc.update("update app_setting set value = '\"\"' where key = 'company.name'");
        caches.getCache("settings").clear();
    }

    private UsernamePasswordAuthenticationToken auth(Role role) {
        var p = new StaffPrincipal(adminId, "a@x.ph", "Ann Admin", Set.of(role), Set.of());
        return new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MockHttpServletRequestBuilder putJson(String body) {
        return put("/api/admin/settings").with(authentication(auth(Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void seededDefaultsReadable() throws Exception {
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo("15");
        assertThat(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_VALUE_MAX)).isEqualByComparingTo("5000");
        assertThat(settings.bankAccounts()).isEmpty();
        mvc.perform(get("/api/admin/settings").with(authentication(auth(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['approval.discountPercentMax']").value(15))
                .andExpect(jsonPath("$['invoice.terms']").value(""));
    }

    @Test
    void writeOffValueMaxDefaults5000() {
        assertThat(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_VALUE_MAX)).isEqualByComparingTo("5000");
    }

    @Test
    void oldWriteOffQtyKeyGone() throws Exception {
        mvc.perform(putJson("{\"approval.writeOffQtyMax\": 5}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateIsAudited() throws Exception {
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo("15"); // warm cache
        int before = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class);
        mvc.perform(putJson("{\"approval.discountPercentMax\": 20, \"invoice.terms\": \"Net 30\"}"))
                .andExpect(status().isOk());
        assertThat(settings.decimal(SettingKey.APPROVAL_DISCOUNT_PERCENT_MAX)).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(settings.text(SettingKey.INVOICE_TERMS)).isEqualTo("Net 30");
        assertThat(jdbc.queryForObject("select updated_by from app_setting where key = 'approval.discountPercentMax'",
                Long.class)).isEqualTo(adminId);
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
        mvc.perform(putJson("{\"approval.discountPercentMax\": 15, \"approval.writeOffValueMax\": 5000.0,"
                + " \"invoice.terms\": \"\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void unchangedFallbackAdminWritesNoAudit() throws Exception {
        String body = "{\"followUps.fallbackAdminId\": " + adminId + "}";
        mvc.perform(putJson(body)).andExpect(status().isOk());
        int before = jdbc.queryForObject(
                "select count(*) from audit_event where action = 'SETTING_CHANGED'", Integer.class);
        mvc.perform(putJson(body)).andExpect(status().isOk());
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
        mvc.perform(putJson("{\"approval.writeOffValueMax\": 6000, \"approval.discountPercentMax\": 101}"))
                .andExpect(status().isBadRequest());
        assertThat(settings.decimal(SettingKey.APPROVAL_WRITE_OFF_VALUE_MAX)).isEqualByComparingTo("5000");
    }

    @Test
    void nonNumericRejected() throws Exception {
        mvc.perform(putJson("{\"approval.discountPercentMax\": \"abc\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(putJson("{\"invoice.terms\": 5}")).andExpect(status().isBadRequest());
    }

    @Test
    void bankAccountsSavedWithQrButTheLogKeepsNoImage() throws Exception {
        mvc.perform(putJson("{\"invoice.bankAccounts\": [{\"bankName\": \"BDO\", \"accountName\": \"TCJ\", \"accountNumber\": \"123\", \"qr\": \"data:image/png;base64,iVBORw0KGgo=\"}]}")).andExpect(status().isOk());
        assertThat(settings.bankAccounts()).hasSize(1);
        assertThat(settings.bankAccounts().getFirst()).containsEntry("bankName", "BDO");
        assertThat(jdbc.queryForObject("select changes::text from audit_event where summary like '%invoice.bankAccounts%' order by id desc limit 1", String.class))
                .contains("(image)").doesNotContain("base64");
    }

    @Test
    void bankAccountNeedsANumberAndARealImage() throws Exception {
        mvc.perform(putJson("{\"invoice.bankAccounts\": [{\"bankName\": \"BDO\"}]}")).andExpect(status().isBadRequest());
        mvc.perform(putJson("{\"invoice.bankAccounts\": [{\"bankName\": \"BDO\", \"accountNumber\": \"1\", \"qr\": \"data:image/svg+xml;base64,AAAA\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followUpSettingsHaveDefaults() throws Exception {
        mvc.perform(get("/api/admin/settings").with(authentication(auth(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['followUps.restock.fallbackDays']").value(15))
                .andExpect(jsonPath("$['followUps.restock.rhythmPercent']").value(80))
                .andExpect(jsonPath("$['followUps.auto.RESTOCK.enabled']").value(true))
                .andExpect(jsonPath("$['followUps.fallbackAdminId']").value((Object) null));
        assertThat(settings.bool(SettingKey.FOLLOW_UPS_AUTO_RESTOCK_ENABLED)).isTrue();
        assertThat(settings.integer(SettingKey.FOLLOW_UPS_RESTOCK_FALLBACK_DAYS)).isEqualTo(15);
        assertThat(settings.longOrNull(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID)).isNull();
    }

    @Test
    void followUpSettingsValidate() throws Exception {
        mvc.perform(putJson("{\"followUps.winBack.days\": 3}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(putJson("{\"followUps.winBack.days\": 7.5}")).andExpect(status().isBadRequest());
        mvc.perform(putJson("{\"followUps.winBack.days\": 90}")).andExpect(status().isOk())
                .andExpect(jsonPath("$['followUps.winBack.days']").value(90));
        assertThat(settings.integer(SettingKey.FOLLOW_UPS_WIN_BACK_DAYS)).isEqualTo(90);
        mvc.perform(putJson("{\"followUps.auto.RESTOCK.enabled\": \"yes\"}")).andExpect(status().isBadRequest());
        mvc.perform(putJson("{\"followUps.auto.RESTOCK.enabled\": false}")).andExpect(status().isOk());
        assertThat(settings.bool(SettingKey.FOLLOW_UPS_AUTO_RESTOCK_ENABLED)).isFalse();
        mvc.perform(putJson("{\"followUps.fallbackAdminId\": 0}")).andExpect(status().isBadRequest());
        mvc.perform(putJson("{\"followUps.fallbackAdminId\": " + adminId + "}")).andExpect(status().isOk());
        assertThat(settings.longOrNull(SettingKey.FOLLOW_UPS_FALLBACK_ADMIN_ID)).isEqualTo(adminId);
        mvc.perform(putJson("{\"followUps.fallbackAdminId\": null}")).andExpect(status().isOk())
                .andExpect(jsonPath("$['followUps.fallbackAdminId']").value((Object) null));
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
        mvc.perform(put("/api/admin/settings").with(authentication(auth(Role.SALES))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void textSettingTooLongRejected() throws Exception {
        mvc.perform(putJson("{\"company.tin\": \"" + "1".repeat(33) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void quoteValidityZeroRejected() throws Exception {
        mvc.perform(putJson("{\"sales.quoteValidityDays\": 0}")).andExpect(status().isBadRequest());
    }

    @Test
    void companyNameRoundTrips() throws Exception {
        assertThat(settings.text(SettingKey.COMPANY_NAME)).isEmpty();
        mvc.perform(putJson("{\"company.name\": \"  The Coffee Junkie Inc.  \"}")).andExpect(status().isOk());
        assertThat(settings.text(SettingKey.COMPANY_NAME)).isEqualTo("The Coffee Junkie Inc.");
        mvc.perform(get("/api/admin/settings").with(authentication(auth(Role.ADMIN))))
                .andExpect(jsonPath("$['company.name']").value("The Coffee Junkie Inc."))
                .andExpect(jsonPath("$['sales.quoteValidityDays']").value(15));
    }
}
