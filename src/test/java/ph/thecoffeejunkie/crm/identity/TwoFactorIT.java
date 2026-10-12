package ph.thecoffeejunkie.crm.identity;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.EncryptionService;
import ph.thecoffeejunkie.crm.shared.Role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class TwoFactorIT {

    static final String PW = "correct-horse-battery";
    static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired EncryptionService encryption;
    @Value("${app.security.encryption-key}") String encryptionKey;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("delete from recovery_code where user_id in (select id from app_user where email like '%@totp.it')");
        jdbc.update("delete from app_user where email like '%@totp.it'");
        var keys = redis.keys("login:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private AppUser user(String email, Role role) {
        var u = new AppUser();
        u.email = email;
        u.passwordHash = BCRYPT.encode(PW);
        u.firstName = "Tina";
        u.lastName = "Otp";
        u.role = role;
        return users.save(u);
    }

    /** A user with 2FA already on; returns the raw secret. */
    private byte[] enrolled(String email, Role role) throws Exception {
        AppUser u = user(email, role);
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        String enc = encryption.encrypt(Base32.encode(secret), encryption.getKeyFromPassword(encryptionKey));
        jdbc.update("update app_user set totp_secret_enc = ?, totp_enabled_at = now() where id = ?", enc, u.id);
        return secret;
    }

    /** Replay protection would otherwise make a second login in the same 30 s window fail. */
    private String code(String email, byte[] secret) {
        jdbc.update("update app_user set totp_last_step = null where email = ?", email);
        return String.format("%06d", Totp.codeAt(secret, Totp.stepAt(Instant.now())));
    }

    private static String wrong(byte[] secret) {
        long step = Totp.stepAt(Instant.now());
        for (int c = 0; ; c++) {
            boolean clash = false;
            for (long s = step - 1; s <= step + 1; s++) {
                clash |= Totp.codeAt(secret, s) == c;
            }
            if (!clash) {
                return String.format("%06d", c);
            }
        }
    }

    private MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}").with(csrf(mvc)))
                .andExpect(status().isOk()).andReturn();
    }

    private static Cookie session(MvcResult r) {
        return r.getResponse().getCookie("TCJSESSION");
    }

    private ResultActions send(String method, String url, Cookie s, String json) throws Exception {
        var b = "DELETE".equals(method) ? delete(url) : post(url);
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json).cookie(s).with(csrf(mvc)));
    }

    private Cookie fullSession(String email, byte[] secret) throws Exception {
        return fullSession(email, secret, code(email, secret));
    }

    private Cookie fullSession(String email, byte[] secret, String code) throws Exception {
        Cookie pre = session(login(email));
        return session(send("POST", "/api/auth/totp", pre, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.next").value("DONE")).andReturn());
    }

    private static String next(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.next");
    }

    @Test
    void adminEnrolsThenFullAccess() throws Exception {
        user("enrol@totp.it", Role.ADMIN);
        MvcResult l = login("enrol@totp.it");
        assertThat(next(l)).isEqualTo("ENROL_TOTP");
        Cookie s = session(l);
        MvcResult setup = send("POST", "/api/me/totp/setup", s, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.qrSvg").isNotEmpty()).andReturn();
        String body = setup.getResponse().getContentAsString();
        String base32 = JsonPath.read(body, "$.secret");
        assertThat((String) JsonPath.read(body, "$.otpauthUri")).isEqualTo(
                "otpauth://totp/TCJ%20CRM:enrol@totp.it?secret=" + base32
                        + "&issuer=TCJ%20CRM&algorithm=SHA1&digits=6&period=30");

        String code = String.format("%06d", Totp.codeAt(Base32.decode(base32), Totp.stepAt(Instant.now())));
        MvcResult enabled = send("POST", "/api/me/totp/enable", s, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.codes.length()").value(10)).andReturn();
        List<String> codes = JsonPath.read(enabled.getResponse().getContentAsString(), "$.codes");
        assertThat(codes).allMatch(c -> c.matches("[a-z2-7]{5}-[a-z2-7]{5}"));

        mvc.perform(get("/api/me").cookie(session(enabled))).andExpect(status().isOk());

        // secretEncryptedAtRest
        String stored = jdbc.queryForObject("select totp_secret_enc from app_user where email = 'enrol@totp.it'",
                String.class);
        assertThat(stored).isNotEqualTo(base32);
        assertThat(encryption.decrypt(stored, encryption.getKeyFromPassword(encryptionKey))).isEqualTo(base32);
    }

    @Test
    void enableWithWrongCodeRejected() throws Exception {
        user("badenrol@totp.it", Role.ADMIN);
        Cookie s = session(login("badenrol@totp.it"));
        send("POST", "/api/me/totp/setup", s, "").andExpect(status().isOk());
        send("POST", "/api/me/totp/enable", s, "{\"code\":\"000000\"}").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));
        mvc.perform(get("/api/me").cookie(s)).andExpect(status().isForbidden());
    }

    @Test
    void loginRequiresTotpOnceEnabled() throws Exception {
        byte[] secret = enrolled("req@totp.it", Role.SALES);
        MvcResult l = login("req@totp.it");
        assertThat(next(l)).isEqualTo("TOTP");
        mvc.perform(get("/api/me").cookie(session(l))).andExpect(status().isForbidden());
        Cookie full = fullSession("req@totp.it", secret);
        mvc.perform(get("/api/me").cookie(full)).andExpect(status().isOk());
    }

    @Test
    void totpEndpointWithoutPreSessionIs401() throws Exception {
        mvc.perform(post("/api/auth/totp").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}")
                .with(csrf(mvc))).andExpect(status().isUnauthorized());
    }

    @Test
    void sameCodeCannotBeReused() throws Exception {
        byte[] secret = enrolled("replay@totp.it", Role.SALES);
        String used = code("replay@totp.it", secret);
        fullSession("replay@totp.it", secret, used);
        Cookie pre = session(login("replay@totp.it"));
        send("POST", "/api/auth/totp", pre, "{\"code\":\"" + used + "\"}").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));
    }

    @Test
    void parallelSameCodeOnlyOneWins() throws Exception {
        byte[] secret = enrolled("race@totp.it", Role.SALES);
        Cookie a = session(login("race@totp.it"));
        Cookie b = session(login("race@totp.it"));
        String body = "{\"code\":\"" + code("race@totp.it", secret) + "\"}";
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie c : List.of(a, b)) {
                var csrf = csrf(mvc);
                results.add(pool.submit(() -> {
                    barrier.await();
                    return mvc.perform(post("/api/auth/totp").contentType(MediaType.APPLICATION_JSON)
                            .content(body).cookie(c).with(csrf)).andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (var f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void recoveryCodeWorksOnce() throws Exception {
        enrolled("rec@totp.it", Role.SALES);
        long id = users.findByEmailAndArchivedAtIsNull("rec@totp.it").orElseThrow().id;
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("abcde-fghij".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("insert into recovery_code (user_id, code_hash) values (?, ?)", id, hash);

        Cookie pre = session(login("rec@totp.it"));
        MvcResult ok = send("POST", "/api/auth/totp", pre, "{\"recoveryCode\":\"abcde-fghij\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.next").value("DONE")).andReturn();
        mvc.perform(get("/api/me").cookie(session(ok))).andExpect(status().isOk());

        Cookie pre2 = session(login("rec@totp.it"));
        send("POST", "/api/auth/totp", pre2, "{\"recoveryCode\":\"abcde-fghij\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOTP_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'RECOVERY_CODE_USED' "
                + "and entity_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void wrongTotpCountsTowardLockout() throws Exception {
        byte[] secret = enrolled("lock@totp.it", Role.SALES);
        Cookie pre = session(login("lock@totp.it"));
        String bad = "{\"code\":\"" + wrong(secret) + "\"}";
        for (int i = 0; i < 5; i++) {
            send("POST", "/api/auth/totp", pre, bad).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOTP_INVALID"));
        }
        send("POST", "/api/auth/totp", pre, "{\"code\":\"" + code("lock@totp.it", secret) + "\"}")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(jdbc.queryForObject("select locked_until is not null from app_user where email = 'lock@totp.it'",
                Boolean.class)).isTrue();
    }

    @Test
    void adminCannotDisableOwnTotp() throws Exception {
        byte[] secret = enrolled("nodis@totp.it", Role.ADMIN);
        Cookie s = fullSession("nodis@totp.it", secret);
        send("DELETE", "/api/me/totp", s, "{\"code\":\"" + code("nodis@totp.it", secret) + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void salesCanDisableOwnTotp() throws Exception {
        byte[] secret = enrolled("dis@totp.it", Role.SALES);
        Cookie s = fullSession("dis@totp.it", secret);
        send("DELETE", "/api/me/totp", s, "{\"code\":\"" + wrong(secret) + "\"}").andExpect(status().isUnauthorized());
        send("DELETE", "/api/me/totp", s, "{\"code\":\"" + code("dis@totp.it", secret) + "\"}")
                .andExpect(status().isNoContent());
        assertThat(next(login("dis@totp.it"))).isEqualTo("DONE");
    }

    @Test
    void adminResetClearsAndRevokes() throws Exception {
        byte[] adminSecret = enrolled("boss@totp.it", Role.ADMIN);
        byte[] secret = enrolled("target@totp.it", Role.SALES);
        Cookie admin = fullSession("boss@totp.it", adminSecret);
        Cookie target = fullSession("target@totp.it", secret);
        long id = users.findByEmailAndArchivedAtIsNull("target@totp.it").orElseThrow().id;
        jdbc.update("insert into recovery_code (user_id, code_hash) values (?, 'x')", id);

        send("POST", "/api/admin/users/" + id + "/totp-reset", admin, "").andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(target)).andExpect(status().isUnauthorized());
        assertThat(next(login("target@totp.it"))).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select count(*) from recovery_code where user_id = ?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("select totp_secret_enc is null and totp_enabled_at is null "
                + "from app_user where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void nonAdminCannotReset() throws Exception {
        byte[] secret = enrolled("peon@totp.it", Role.SALES);
        Cookie s = fullSession("peon@totp.it", secret);
        send("POST", "/api/admin/users/1/totp-reset", s, "").andExpect(status().isForbidden());
    }

    @Test
    void pendingSecretCannotCrossUsers() throws Exception {
        user("a@totp.it", Role.SALES);
        user("b@totp.it", Role.SALES);
        Cookie a = session(login("a@totp.it"));
        String base32 = JsonPath.read(send("POST", "/api/me/totp/setup", a, "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.secret");
        // Same session id logs in as B (login rotates the id but keeps attributes).
        Cookie b = session(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"b@totp.it\",\"password\":\"" + PW + "\"}").cookie(a).with(csrf(mvc)))
                .andExpect(status().isOk()).andReturn());
        String code = String.format("%06d", Totp.codeAt(Base32.decode(base32), Totp.stepAt(Instant.now())));
        send("POST", "/api/me/totp/enable", b, "{\"code\":\"" + code + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select totp_enabled_at is null from app_user where email = 'b@totp.it'",
                Boolean.class)).isTrue();
    }

    @Test
    void preTotpSessionCannotSetup() throws Exception {
        enrolled("pre@totp.it", Role.SALES);
        send("POST", "/api/me/totp/setup", session(login("pre@totp.it")), "").andExpect(status().isForbidden());
    }

    @Test
    void parallelSameRecoveryCodeOnlyOneWins() throws Exception {
        enrolled("recrace@totp.it", Role.SALES);
        long id = users.findByEmailAndArchivedAtIsNull("recrace@totp.it").orElseThrow().id;
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("abcde-fghij".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("insert into recovery_code (user_id, code_hash) values (?, ?)", id, hash);
        Cookie a = session(login("recrace@totp.it"));
        Cookie b = session(login("recrace@totp.it"));
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie c : List.of(a, b)) {
                var csrf = csrf(mvc);
                results.add(pool.submit(() -> {
                    barrier.await();
                    return mvc.perform(post("/api/auth/totp").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recoveryCode\":\"abcde-fghij\"}").cookie(c).with(csrf))
                            .andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (var f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            pool.shutdownNow();
        }
    }
}
