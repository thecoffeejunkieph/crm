package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterIT {

    @Autowired MockMvc mvc;
    @Autowired AccessLogFilter filter;

    private static RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(new StaffPrincipal(1, "a@x.it", "Admin", Role.ADMIN,
                Set.of()), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void okRequestLogsInfo(CapturedOutput out) throws Exception {
        mvc.perform(get("/api/customers").with(admin()));
        assertThat(out.getOut()).containsPattern("INFO .*crm\\.access.*GET /api/customers → 200 in \\d+ms user=user:1 ADMIN");
    }

    @Test
    void badRequestLogsWarnWithCode(CapturedOutput out) throws Exception {
        mvc.perform(get("/api/follow-ups").param("bucket", "soon").with(admin()));
        assertThat(out.getOut()).containsPattern("WARN .*GET /api/follow-ups → 400 in \\d+ms .*code=VALIDATION_FAILED");
    }

    @Test
    void queryValuesNotLogged(CapturedOutput out) throws Exception {
        mvc.perform(get("/api/customers").param("q", "secretvalue").with(admin()));
        assertThat(out.getOut()).contains("q=[q]").doesNotContain("secretvalue");
    }

    @Test
    void streamAndHealthSkipped() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/notifications/stream"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/customers"))).isFalse();
    }
}
