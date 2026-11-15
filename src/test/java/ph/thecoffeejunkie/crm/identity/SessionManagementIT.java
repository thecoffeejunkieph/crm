package ph.thecoffeejunkie.crm.identity;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SessionManagementIT {

    static final String PW = "correct-horse-battery";
    static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired LoginThrottle throttle;

    @BeforeEach
    @AfterEach
    void clean() {
        // authentication() post-processors leave their context on the thread; it would hide the CSRF cookie later.
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        jdbc.update("delete from app_user where email like '%@sessions.it'");
        var keys = redis.keys("login:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private AppUser user(String email, Role role) {
        var u = new AppUser();
        u.email = email;
        u.passwordHash = BCRYPT.encode(PW);
        u.firstName = "Ana";
        u.lastName = "Cruz";
        u.role = role;
        return users.save(u);
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private Cookie login(String email) throws Exception {
        return login(email, PW);
    }

    private Cookie login(String email, String password) throws Exception {
        Cookie x = xsrf();
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                        .cookie(x).header("X-XSRF-TOKEN", x.getValue()))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("TCJSESSION");
    }

    private String sessions(Cookie c) throws Exception {
        return mvc.perform(get("/api/me/sessions").cookie(c)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static RequestPostProcessor as(long id, Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(id, "x@sessions.it", "X", role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static String hashWhere(String json, boolean current) {
        List<String> ids = JsonPath.read(json, "$[?(@.current==" + current + ")].id");
        return ids.get(0);
    }

    @Test
    void listsOwnSessionsWithCurrentFlag() throws Exception {
        user("list@sessions.it", Role.SALES);
        Cookie a = login("list@sessions.it");
        login("list@sessions.it");
        String json = sessions(a);
        assertThat(JsonPath.<List<Object>>read(json, "$")).hasSize(2);
        assertThat(JsonPath.<List<Object>>read(json, "$[?(@.current==true)]")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$[0].clientType")).isEqualTo("WEB");
    }

    @Test
    void sessionIdNeverExposed() throws Exception {
        user("raw@sessions.it", Role.SALES);
        Cookie a = login("raw@sessions.it");
        Cookie b = login("raw@sessions.it");
        String json = sessions(a);
        for (Cookie c : List.of(a, b)) {
            // the cookie holds the base64 of the real id
            String raw = new String(java.util.Base64.getDecoder().decode(c.getValue()));
            assertThat(json).doesNotContain(c.getValue()).doesNotContain(raw);
        }
    }

    @Test
    void revokeOtherDevice() throws Exception {
        long id = user("other@sessions.it", Role.SALES).id;
        Cookie a = login("other@sessions.it");
        Cookie b = login("other@sessions.it");
        String hash = hashWhere(sessions(a), false);
        mvc.perform(delete("/api/me/sessions/" + hash).cookie(a).with(csrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(a)).andExpect(status().isOk());
        mvc.perform(delete("/api/me/sessions/" + hash).cookie(a).with(csrf(mvc))).andExpect(status().isNotFound());
        assertThat(audits("SESSION_REVOKED", id)).isEqualTo(1);
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        user("a@sessions.it", Role.SALES);
        user("b@sessions.it", Role.SALES);
        Cookie a = login("a@sessions.it");
        Cookie b = login("b@sessions.it");
        String bHash = hashWhere(sessions(b), true);
        mvc.perform(delete("/api/me/sessions/" + bHash).cookie(a).with(csrf(mvc))).andExpect(status().isNotFound());
        mvc.perform(get("/api/me").cookie(b)).andExpect(status().isOk());
    }

    @Test
    void revokeAllOthersKeepsCurrent() throws Exception {
        user("all@sessions.it", Role.SALES);
        Cookie a = login("all@sessions.it");
        Cookie b = login("all@sessions.it");
        Cookie c = login("all@sessions.it");
        mvc.perform(delete("/api/me/sessions").cookie(a).with(csrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(a)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRevokesUsersSessions() throws Exception {
        AppUser u = user("target@sessions.it", Role.SALES);
        Cookie a = login("target@sessions.it");
        Cookie b = login("target@sessions.it");
        String url = "/api/admin/users/" + u.id + "/sessions";
        String json = mvc.perform(get(url).with(as(9999L, Role.ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString();
        String hash = JsonPath.<List<String>>read(json, "$[*].id").get(0);
        mvc.perform(delete(url + "/" + hash).with(as(9999L, Role.ADMIN)).with(csrf(mvc)))
                .andExpect(status().isNoContent());
        mvc.perform(get(url).with(as(9999L, Role.ADMIN))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete(url).with(as(9999L, Role.ADMIN)).with(csrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get(url).with(as(9999L, Role.ADMIN))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me").cookie(a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());
        assertThat(audits("SESSION_REVOKED", u.id)).isEqualTo(2);
        mvc.perform(get("/api/admin/users/999999999/sessions").with(as(9999L, Role.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotRevokeSessionThroughWrongUser() throws Exception {
        AppUser x = user("x@sessions.it", Role.SALES);
        AppUser y = user("y@sessions.it", Role.SALES);
        Cookie xs = login("x@sessions.it");
        String xHash = hashWhere(sessions(xs), true);
        mvc.perform(delete("/api/admin/users/" + y.id + "/sessions/" + xHash).with(as(9999L, Role.ADMIN))
                .with(csrf(mvc))).andExpect(status().isNotFound());
        mvc.perform(get("/api/me").cookie(xs)).andExpect(status().isOk());
        assertThat(audits("SESSION_REVOKED", x.id) + audits("SESSION_REVOKED", y.id)).isZero();
    }

    @Test
    void salesCannotListOthersSessions() throws Exception {
        AppUser u = user("victim@sessions.it", Role.SALES);
        mvc.perform(get("/api/admin/users/" + u.id + "/sessions").with(as(9999L, Role.SALES)))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeKeepsCurrentRevokesOthers() throws Exception {
        long id = user("pw@sessions.it", Role.SALES).id;
        Cookie a = login("pw@sessions.it");
        Cookie b = login("pw@sessions.it");
        mvc.perform(post("/api/me/password").cookie(a).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password-1\",\"newPassword\":\"brand-new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("currentPassword"));
        mvc.perform(post("/api/me/password").cookie(a).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"brand-new-password-1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(a)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(b)).andExpect(status().isUnauthorized());
        assertThat(audits("PASSWORD_CHANGED", id)).isEqualTo(1);
        login("pw@sessions.it", "brand-new-password-1");
        Cookie x = xsrf();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pw@sessions.it\",\"password\":\"" + PW + "\"}")
                        .cookie(x).header("X-XSRF-TOKEN", x.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeClearsLoginThrottle() throws Exception {
        user("lock@sessions.it", Role.SALES);
        Cookie a = login("lock@sessions.it");
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("lock@sessions.it");
        }
        assertThat(throttle.isLocked("lock@sessions.it")).isTrue();
        mvc.perform(post("/api/me/password").cookie(a).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"brand-new-password-1\"}"))
                .andExpect(status().isNoContent());
        assertThat(throttle.isLocked("lock@sessions.it")).isFalse();
        login("lock@sessions.it", "brand-new-password-1");
    }

    @Test
    void wrongCurrentPasswordCountsTowardsLockout() throws Exception {
        user("brute@sessions.it", Role.SALES);
        Cookie a = login("brute@sessions.it");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/me/password").cookie(a).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong-password-1\",\"newPassword\":\"brand-new-password-1\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(throttle.isLocked("brute@sessions.it")).isTrue();
    }

    private int audits(String action, long userId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_type = 'USER' "
                + "and entity_id = ?", Integer.class, action, userId);
    }
}
