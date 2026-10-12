package ph.thecoffeejunkie.crm.identity;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@MailIntegrationTest
class PasswordResetIT {

    static final String PW = "correct-horse-battery";
    static final String NEW_PW = "brand-new-password-1";
    static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired JavaMailSender sender;
    @Autowired OneTimeTokens tokens;

    @BeforeEach
    void setUp() {
        clean();
        MailSupport.stub(sender);
    }

    @AfterEach
    void clean() {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        jdbc.update("delete from one_time_token where subject_type = 'USER' and subject_id in "
                + "(select id from app_user where email like '%@reset.it')");
        jdbc.update("delete from app_user where email like '%@reset.it'");
        var keys = redis.keys("login:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private AppUser user(String email, String password) {
        var u = new AppUser();
        u.email = email;
        u.passwordHash = password == null ? null : BCRYPT.encode(password);
        u.firstName = "Ana";
        u.lastName = "Cruz";
        u.role = Role.SALES;
        return users.save(u);
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/password/forgot").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/password/reset").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void forgotForUnknownEmailStill202AndSendsNothing() throws Exception {
        forgot("nobody@reset.it").andExpect(status().isAccepted());
        assertThat(MailSupport.sent(sender)).isEmpty();
    }

    @Test
    void forgotSendsNothingForPendingInviteOrArchivedUser() throws Exception {
        user("pending@reset.it", null);
        user("archived@reset.it", PW);
        jdbc.update("update app_user set archived_at = now() where email = 'archived@reset.it'");
        forgot("pending@reset.it").andExpect(status().isAccepted());
        forgot("archived@reset.it").andExpect(status().isAccepted());
        assertThat(MailSupport.sent(sender)).isEmpty();
    }

    @Test
    void resetUnlocksAndRevokesSessions() throws Exception {
        AppUser u = user("locked@reset.it", PW);
        Cookie session = login(u.email, PW).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("TCJSESSION");
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            login(u.email, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(u.email, PW).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        Map<String, Object> locked = jdbc.queryForMap(
                "select failed_logins, locked_until from app_user where id = ?", u.id);
        assertThat(locked.get("locked_until")).isNotNull();

        forgot("LOCKED@reset.it").andExpect(status().isAccepted());
        List<MimeMessage> mails = MailSupport.sent(sender);
        assertThat(mails).hasSize(1);
        assertThat(MailSupport.link(mails.get(0))).startsWith("http://app.test/reset-password/");
        String token = MailSupport.token(mails.get(0));

        reset(token, NEW_PW).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "select failed_logins, locked_until from app_user where id = ?", u.id);
        assertThat(row.get("failed_logins")).isEqualTo(0);
        assertThat(row.get("locked_until")).isNull();
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        login(u.email, PW).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        login(u.email, NEW_PW).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PASSWORD_RESET' "
                + "and entity_id = ?", Integer.class, u.id)).isEqualTo(1);

        reset(token, NEW_PW + "x").andExpect(status().isGone()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void weakPasswordKeepsTokenUsable() throws Exception {
        user("weak@reset.it", PW);
        forgot("weak@reset.it").andExpect(status().isAccepted());
        String token = MailSupport.token(MailSupport.sent(sender).get(0));
        reset(token, "short").andExpect(status().isBadRequest());
        reset(token, NEW_PW).andExpect(status().isNoContent());
    }

    @Test
    void newRequestInvalidatesEarlierLink() throws Exception {
        user("twice@reset.it", PW);
        forgot("twice@reset.it").andExpect(status().isAccepted());
        forgot("twice@reset.it").andExpect(status().isAccepted());
        List<MimeMessage> mails = MailSupport.sent(sender);
        assertThat(mails).hasSize(2);
        reset(MailSupport.token(mails.get(0)), NEW_PW).andExpect(status().isGone());
        reset(MailSupport.token(mails.get(1)), NEW_PW).andExpect(status().isNoContent());
    }

    @Test
    void inviteTokenCannotResetPassword() throws Exception {
        AppUser u = user("cross@reset.it", PW);
        String invite = tokens.issue(TokenPurpose.INVITE, "USER", u.id, java.time.Duration.ofHours(1), null);
        reset(invite, NEW_PW).andExpect(status().isGone());
        assertThat(BCRYPT.matches(PW, jdbc.queryForObject(
                "select password_hash from app_user where id = ?", String.class, u.id))).isTrue();
    }
}
