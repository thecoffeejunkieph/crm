package ph.thecoffeejunkie.crm.portal;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.identity.SessionAdmin;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@MailIntegrationTest
class PortalAuthIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender sender;
    @Autowired ThreadPoolTaskExecutor applicationTaskExecutor;
    @Autowired SessionAdmin sessionAdmin;
    @Autowired FindByIndexNameSessionRepository<?> sessionRepo;

    private final String tag = "PO" + UUID.randomUUID().toString().substring(0, 8);
    private long admin;
    private long customerId;
    private long contactId;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        MailSupport.stub(sender);
        admin = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'A', 'U', 'ADMIN') returning id", Long.class, tag + "adm@portal.it");
        String res = asAdmin(post("/api/customers"), "{\"type\":\"BUSINESS\",\"displayName\":\"" + tag
                + "\",\"phone\":\"1\",\"paymentTerms\":\"NET_30\"}").andReturn().getResponse().getContentAsString();
        customerId = ((Number) JsonPath.read(res, "$.id")).longValue();
        email = tag.toLowerCase() + "@portal.ph";
        res = asAdmin(post("/api/customers/" + customerId + "/contacts"), contactJson(email, true, null))
                .andReturn().getResponse().getContentAsString();
        contactId = ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        jdbc.update("delete from one_time_token where subject_type='CONTACT' and subject_id=?", contactId);
        jdbc.update("delete from customer_contact where customer_id=?", customerId);
        jdbc.update("delete from customer where id=?", customerId);
        jdbc.update("delete from app_user where lower(email) like lower(?)", tag + "%");
    }

    private static String contactJson(String email, boolean portal, Long version) {
        return "{\"name\":\"Cee\",\"email\":\"" + email + "\",\"portalEnabled\":" + portal
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private static UsernamePasswordAuthenticationToken staff(long id) {
        return new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(id, "a@portal.it", "A U", Role.ADMIN, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private ResultActions asAdmin(MockHttpServletRequestBuilder b, String body) throws Exception {
        return mvc.perform(b.with(authentication(staff(admin))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is2xxSuccessful());
    }

    private void requestLink(String to) throws Exception {
        mvc.perform(post("/api/portal/auth/request-link").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + to + "\"}")).andExpect(status().isAccepted());
    }

    private String mailedToken() throws Exception {
        requestLink(email);
        return MailSupport.token(MailSupport.awaitMail(sender, 1).get(0));
    }

    private ResultActions redeem(String token) throws Exception {
        return mvc.perform(post("/api/portal/auth/redeem").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private Cookie signIn() throws Exception {
        Cookie c = redeem(mailedToken()).andExpect(status().isOk()).andReturn().getResponse().getCookie("TCJSESSION");
        assertThat(c).isNotNull();
        return c;
    }

    @Test
    void requestLinkAlways202AndOnlyMailsKnownContacts() throws Exception {
        requestLink(tag.toLowerCase() + "nobody@portal.ph");
        requestLink(email.toUpperCase());
        MailSupport.awaitIdle(applicationTaskExecutor);
        var mails = MailSupport.awaitMail(sender, 1);
        assertThat(mails.get(0).getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(MailSupport.link(mails.get(0))).startsWith("http://app.test/portal/signin/");
    }

    @Test
    void portalDisabledContactGetsNoMail() throws Exception {
        asAdmin(put("/api/customers/" + customerId + "/contacts/" + contactId), contactJson(email, false, 0L));
        requestLink(email);
        MailSupport.awaitIdle(applicationTaskExecutor);
        assertThat(MailSupport.sent(sender)).isEmpty();
    }

    @Test
    void redeemCreatesCustomerSession() throws Exception {
        String token = mailedToken();
        assertThat(jdbc.queryForObject("select extract(epoch from expires_at - now()) from one_time_token "
                + "where subject_type='CONTACT' and subject_id=? and used_at is null", Double.class, contactId))
                .isBetween(14 * 60.0, 15 * 60.0);
        Cookie c = redeem(token).andExpect(status().isOk()).andReturn().getResponse().getCookie("TCJSESSION");
        assertThat(sessionRepo.findByPrincipalName("contact:" + contactId).values())
                .singleElement().satisfies(x -> assertThat(x.getMaxInactiveInterval()).isEqualTo(Duration.ofDays(7)));
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId").value(contactId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.customerName").value(tag))
                .andExpect(jsonPath("$.email").value(email));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action='PORTAL_LOGIN' "
                + "and actor_type='CONTACT' and actor_id=?", Integer.class, contactId)).isEqualTo(1);
    }

    @Test
    void tokenSingleUse() throws Exception {
        String token = mailedToken();
        redeem(token).andExpect(status().isOk());
        redeem(token).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void expiredAfter15Minutes() throws Exception {
        String token = mailedToken();
        jdbc.update("update one_time_token set expires_at = now() - interval '1 second' "
                + "where subject_type='CONTACT' and subject_id=?", contactId);
        redeem(token).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void contactCannotCallStaffApi() throws Exception {
        mvc.perform(get("/api/customers").cookie(signIn())).andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCallPortalApi() throws Exception {
        mvc.perform(get("/api/portal/me").with(authentication(staff(admin)))).andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsSession() throws Exception {
        Cookie c = signIn();
        mvc.perform(post("/api/portal/auth/logout").cookie(c).with(csrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void archivedContactLosesSession() throws Exception {
        Cookie c = signIn();
        asAdmin(post("/api/customers/" + customerId + "/contacts/" + contactId + "/archive"), "");
        assertThat(sessionAdmin.list("contact:" + contactId, null)).isEmpty(); // revoked by the hook, not just the guard
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void archivedCustomerLosesSession() throws Exception {
        Cookie c = signIn();
        asAdmin(post("/api/customers/" + customerId + "/archive"), "");
        assertThat(sessionAdmin.list("contact:" + contactId, null)).isEmpty(); // revoked by the hook, not just the guard
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void portalDisabledLosesSession() throws Exception {
        Cookie c = signIn();
        asAdmin(put("/api/customers/" + customerId + "/contacts/" + contactId), contactJson(email, false, 0L));
        assertThat(sessionAdmin.list("contact:" + contactId, null)).isEmpty(); // revoked by the hook, not just the guard
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void emailChangeLosesSession() throws Exception {
        Cookie c = signIn();
        asAdmin(put("/api/customers/" + customerId + "/contacts/" + contactId), contactJson("new" + email, true, 0L));
        assertThat(sessionAdmin.list("contact:" + contactId, null)).isEmpty(); // revoked by the hook, not just the guard
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void guardEndsSessionEvenWithoutRevocation() throws Exception {
        Cookie c = signIn();
        jdbc.update("update customer_contact set archived_at = now() where id = ?", contactId);
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/portal/me").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void staffLoginDropsPortalSessionInsteadOfInheritingItsInterval() throws Exception {
        Cookie portal = signIn();
        jdbc.update("insert into app_user (email, first_name, last_name, role, password_hash) "
                + "values (?, 'S', 'U', 'SALES', ?)", tag.toLowerCase() + "sales@portal.it", new BCryptPasswordEncoder().encode("correct-horse-battery"));
        long staffId = jdbc.queryForObject("select id from app_user where email = ?", Long.class, tag.toLowerCase() + "sales@portal.it");
        mvc.perform(post("/api/auth/login").cookie(portal).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + tag.toLowerCase() + "sales@portal.it\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk());
        assertThat(sessionAdmin.list("contact:" + contactId, null)).isEmpty();
        assertThat(sessionRepo.findByPrincipalName("user:" + staffId).values())
                .singleElement().satisfies(x -> assertThat(x.getMaxInactiveInterval()).isLessThan(Duration.ofDays(1)));
    }
}
