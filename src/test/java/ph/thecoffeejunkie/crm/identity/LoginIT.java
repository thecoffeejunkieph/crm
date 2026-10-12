package ph.thecoffeejunkie.crm.identity;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import com.jayway.jsonpath.JsonPath;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class LoginIT {

    static final String PW = "correct-horse-battery";
    static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired RedisIndexedSessionRepository sessions;
    @Autowired StringRedisTemplate redis;
    @Autowired LoginService loginService;
    @Autowired DataSource dataSource;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("delete from app_user where email like '%@login.it' or email = 'ana@tcj.ph'");
        // Throttle state lives in Redis and would otherwise leak between tests that reuse an email.
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

    private ResultActions login(String email, String password, Cookie... cookies) throws Exception {
        Cookie x = xsrf();
        var req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .cookie(x).header("X-XSRF-TOKEN", x.getValue());
        if (cookies.length > 0) {
            req.cookie(cookies);
        }
        return mvc.perform(req);
    }

    private Cookie loggedIn(String email) throws Exception {
        return login(email, PW).andExpect(status().isOk()).andReturn().getResponse().getCookie("TCJSESSION");
    }

    @Test
    void loginWithoutCsrfFromBrowserRejected() throws Exception {
        user("nocsrf@login.it", Role.SALES);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nocsrf@login.it\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginSetsHttpOnlyLaxCookieAndMeWorks() throws Exception {
        user("cookie@login.it", Role.SALES);
        MockHttpServletResponse res = login("cookie@login.it", PW).andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("DONE"))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andReturn().getResponse();
        String setCookie = res.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("TCJSESSION=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax");
        mvc.perform(get("/api/me").cookie(res.getCookie("TCJSESSION")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cookie@login.it"))
                .andExpect(jsonPath("$.role").value("SALES"));
    }

    @Test
    void emailIsNormalised() throws Exception {
        user("ana@tcj.ph", Role.SALES);
        login("  Ana@TCJ.PH ", PW).andExpect(status().isOk()).andExpect(jsonPath("$.next").value("DONE"));
    }

    @Test
    void wrongPasswordFiveTimesLocks() throws Exception {
        AppUser u = user("locks@login.it", Role.SALES);
        for (int i = 0; i < 5; i++) {
            login("locks@login.it", "wrong-password-123").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        login("locks@login.it", PW).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ACCOUNT_LOCKED' "
                + "and entity_type = 'USER' and entity_id = ?", Integer.class, u.id)).isEqualTo(1);
    }

    @Test
    void unknownEmailSameResponseAsWrongPassword() throws Exception {
        user("known@login.it", Role.SALES);
        var wrong = login("known@login.it", "wrong-password-123").andReturn().getResponse();
        var unknown = login("nobody@login.it", "wrong-password-123").andReturn().getResponse();
        assertThat(unknown.getStatus()).isEqualTo(wrong.getStatus()).isEqualTo(401);
        String code = "\"code\":\"UNAUTHORIZED\"";
        assertThat(wrong.getContentAsString()).contains(code);
        assertThat(unknown.getContentAsString()).contains(code);
    }

    @Test
    void adminWithoutTotpMustEnrol() throws Exception {
        user("admin@login.it", Role.ADMIN);
        Cookie session = login("admin@login.it", PW).andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("ENROL_TOTP"))
                .andReturn().getResponse().getCookie("TCJSESSION");
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void sessionIdRotatesOnLogin() throws Exception {
        user("rotate@login.it", Role.SALES);
        Cookie before = loggedIn("rotate@login.it");
        Cookie after = login("rotate@login.it", PW, before).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("TCJSESSION");
        assertThat(after.getValue()).isNotEqualTo(before.getValue());
        mvc.perform(get("/api/me").cookie(before)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(after)).andExpect(status().isOk());
    }

    @Test
    void absoluteTimeoutEnforced() throws Exception {
        user("old@login.it", Role.SALES);
        Cookie session = loggedIn("old@login.it");
        String id = new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
        // Fixture: age the stored session; Spring Session keeps creationTime as epoch millis in the hash.
        sessions.getSessionRedisOperations().opsForHash().put("spring:session:sessions:" + id, "creationTime",
                Instant.now().minus(Duration.ofHours(13)).toEpochMilli());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(sessions.findById(id)).isNull();
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        user("bye@login.it", Role.SALES);
        Cookie session = loggedIn("bye@login.it");
        Cookie x = xsrf();
        mvc.perform(post("/api/auth/logout").cookie(session, x).header("X-XSRF-TOKEN", x.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    /** Fires 10 simultaneous wrong-password logins; returns the sorted error codes. */
    private List<String> burst(String email) throws Exception {
        var gate = new CyclicBarrier(10);
        Callable<String> attempt = () -> {
            gate.await();
            var res = mvc.perform(post("/api/auth/login").header("X-Client-Type", "ANDROID")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password-123\"}"))
                    .andReturn().getResponse();
            assertThat(res.getStatus()).isEqualTo(401);
            return JsonPath.read(res.getContentAsString(), "$.code");
        };
        var pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(attempt));
            }
            List<String> codes = new ArrayList<>();
            for (Future<String> r : results) {
                codes.add(r.get());
            }
            return codes.stream().sorted().toList();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Invariants that hold for one burst regardless of thread timing: how many attempts slip past the lock check
     * before the lock is set is not deterministic, so exact code counts are not asserted.
     */
    private void assertBurstLocks(String email, Long userId) throws Exception {
        List<String> codes = burst(email);
        assertThat(codes).allMatch(c -> c.equals("UNAUTHORIZED") || c.equals("ACCOUNT_LOCKED"));
        long unauthorized = codes.stream().filter("UNAUTHORIZED"::equals).count();
        assertThat(unauthorized).isGreaterThanOrEqualTo(5);
        login(email, PW).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        String byEntity = userId == null ? "entity_id is null" : "entity_id = " + userId;
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'LOGIN_FAILED' and "
                + byEntity + " and summary = ?", Long.class, "Failed login for " + email)).isEqualTo(unauthorized);
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ACCOUNT_LOCKED' and "
                + byEntity + " and summary like ?", Integer.class, "%" + email + "%")).isEqualTo(1);
    }

    @Test
    void parallelWrongPasswordsAllCountAndLock() throws Exception {
        AppUser u = user("race@login.it", Role.SALES);
        assertBurstLocks("race@login.it", u.id);
        assertThat(jdbc.queryForObject("select locked_until > now() from app_user where id = ?", Boolean.class, u.id))
                .isTrue();
    }

    @Test
    void parallelBurstOnUnknownEmailKeepsTheSameInvariants() throws Exception {
        assertBurstLocks("ghost-" + UUID.randomUUID() + "@login.it", null);
    }

    /** bcrypt must not hold a transaction or a pooled connection, or a login flood drains the pool for everyone. */
    @Test
    void passwordCheckHoldsNoTransactionOrConnection() throws Exception {
        user("notx@login.it", Role.SALES);
        HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
        List<String> seen = new CopyOnWriteArrayList<>();
        PasswordEncoder recording = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return BCRYPT.encode(raw);
            }

            @Override
            public boolean matches(CharSequence raw, String hash) {
                seen.add("tx=" + TransactionSynchronizationManager.isActualTransactionActive()
                        + ",active=" + pool.getActiveConnections());
                return BCRYPT.matches(raw, hash);
            }
        };
        LoginService target = AopTestUtils.getTargetObject(loginService);
        Object original = ReflectionTestUtils.getField(target, "encoder");
        ReflectionTestUtils.setField(target, "encoder", recording);
        try {
            for (String pw : List.of("wrong-password-123", PW)) {
                mvc.perform(post("/api/auth/login").header("X-Client-Type", "ANDROID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notx@login.it\",\"password\":\"" + pw + "\"}"));
            }
        } finally {
            ReflectionTestUtils.setField(target, "encoder", original);
        }
        assertThat(seen).containsExactly("tx=false,active=0", "tx=false,active=0");
    }

    @Test
    void unknownEmailLocksLikeARealAccount() throws Exception {
        String ghost = "ghost-" + UUID.randomUUID() + "@login.it";
        for (int i = 0; i < 5; i++) {
            login(ghost, "wrong-password-123").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        String ghostBody = login(ghost, "wrong-password-123").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        user("reallock@login.it", Role.SALES);
        for (int i = 0; i < 5; i++) {
            login("reallock@login.it", "wrong-password-123").andExpect(status().isUnauthorized());
        }
        String realBody = login("reallock@login.it", PW).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        for (String field : List.of("$.code", "$.detail", "$.title", "$.status")) {
            assertThat((Object) JsonPath.read(ghostBody, field)).isEqualTo(JsonPath.read(realBody, field));
        }
        assertThat((String) JsonPath.read(ghostBody, "$.code")).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void partialSessionCannotUpdateProfile() throws Exception {
        var u = new AppUser();
        u.email = "totp@login.it";
        u.passwordHash = BCRYPT.encode(PW);
        u.firstName = "Tia";
        u.lastName = "Totp";
        u.role = Role.SALES;
        u.totpEnabledAt = Instant.now();
        users.save(u);
        Cookie session = login("totp@login.it", PW).andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("TOTP"))
                .andReturn().getResponse().getCookie("TCJSESSION");
        Cookie x = xsrf();
        mvc.perform(put("/api/me").cookie(session, x).header("X-XSRF-TOKEN", x.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hax\",\"lastName\":\"Or\"}"))
                .andExpect(status().isForbidden());
    }
}
