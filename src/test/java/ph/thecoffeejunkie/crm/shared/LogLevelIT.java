package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class LogLevelIT {

    @Autowired MockMvc mvc;
    @Autowired LoggingSystem logging;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor as(Role role) {
        long id = jdbcUserId;
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(id, "a@x.it", "Staff", role,
                Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static long jdbcUserId = 1;

    @AfterEach
    void reset() {
        logging.setLogLevel("ph.thecoffeejunkie.crm", LogLevel.DEBUG);
    }

    private org.springframework.test.web.servlet.ResultActions setLevel(Role role, String level) throws Exception {
        return mvc.perform(put("/api/admin/log-level").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\":\"" + level + "\"}").with(as(role)));
    }

    @Test
    void adminSwitchesTheAppLogLevel() throws Exception {
        setLevel(Role.ADMIN, "INFO").andExpect(status().isOk()).andExpect(jsonPath("$.level").value("INFO"));
        assertThat(logging.getLoggerConfiguration("ph.thecoffeejunkie.crm").getEffectiveLevel()).isEqualTo(LogLevel.INFO);
        mvc.perform(get("/api/admin/log-level").with(as(Role.ADMIN))).andExpect(jsonPath("$.level").value("INFO"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'SETTING_CHANGED' "
                + "and summary like 'Log level%'", Integer.class)).isPositive();
    }

    @Test
    void onlyAdmin() throws Exception {
        setLevel(Role.SALES, "INFO").andExpect(status().isForbidden());
        assertThat(logging.getLoggerConfiguration("ph.thecoffeejunkie.crm").getEffectiveLevel()).isEqualTo(LogLevel.DEBUG);
    }

    @Test
    void unknownLevel400() throws Exception {
        setLevel(Role.ADMIN, "TRACE").andExpect(status().isBadRequest());
    }

    @Test
    void writesTheLogFile() throws Exception {
        mvc.perform(get("/api/admin/log-level").with(as(Role.ADMIN)));
        Path file = Path.of("build/test-logs/crm.log");
        assertThat(file).exists();
        assertThat(Files.readString(file)).contains("crm.access");
    }
}
