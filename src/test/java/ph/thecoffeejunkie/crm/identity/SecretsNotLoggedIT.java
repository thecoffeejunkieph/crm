package ph.thecoffeejunkie.crm.identity;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

/** With service-call DEBUG on, no password, token, code or link reaches the logs (spec 2026-11-05 logging, §4). */
@MailIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SecretsNotLoggedIT {

    static final String PW = "Sup3r-Secret-Pw!";
    static final String WRONG_PW = "Wr0ng-Secret-Pw!";
    static final String NEW_PW = "N3w-Secret-Pw-2!";
    static final String TOTP = "918273";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired JavaMailSender sender;

    @BeforeEach
    void setUp() {
        clean();
        MailSupport.stub(sender);
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from one_time_token where subject_type = 'USER' and subject_id in "
                + "(select id from app_user where email like '%@secrets.it')");
        jdbc.update("delete from app_user where email like '%@secrets.it'");
        var keys = redis.keys("login:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private ResultActions json(String url, String body) throws Exception {
        return mvc.perform(post(url).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void identityFlowsLeaveNoSecretsInTheLog(CapturedOutput out) throws Exception {
        var u = new AppUser();
        u.email = "ana@secrets.it";
        u.passwordHash = new BCryptPasswordEncoder().encode(PW);
        u.firstName = "Ana";
        u.lastName = "Cruz";
        u.role = Role.SALES;
        users.save(u);

        json("/api/auth/login", "{\"email\":\"ana@secrets.it\",\"password\":\"" + WRONG_PW + "\"}");
        json("/api/auth/login", "{\"email\":\"ana@secrets.it\",\"password\":\"" + PW + "\"}");
        json("/api/auth/totp", "{\"code\":\"" + TOTP + "\"}");
        json("/api/auth/password/forgot", "{\"email\":\"ana@secrets.it\"}");
        List<MimeMessage> mails = MailSupport.awaitMail(sender, 1);
        String link = MailSupport.link(mails.get(0));
        String token = MailSupport.token(mails.get(0));
        json("/api/auth/password/reset", "{\"token\":\"" + token + "\",\"password\":\"" + NEW_PW + "\"}");

        String log = out.getOut();
        assertThat(log).as("service calls were logged at DEBUG").contains("→ ").contains("***");
        for (String secret : List.of(PW, WRONG_PW, NEW_PW, TOTP, token, link)) {
            assertThat(log).as("log must not contain " + secret.substring(0, 3) + "…").doesNotContain(secret);
        }
    }
}
