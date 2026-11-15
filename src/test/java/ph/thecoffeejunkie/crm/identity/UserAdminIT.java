package ph.thecoffeejunkie.crm.identity;

import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.MailIntegrationTest;
import ph.thecoffeejunkie.crm.MailSupport;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;
import ph.thecoffeejunkie.crm.warehouses.WarehouseRequest;
import ph.thecoffeejunkie.crm.warehouses.Warehouses;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@MailIntegrationTest
class UserAdminIT {

    static final String PW = "correct-horse-battery";
    static final String NEW_PW = "brand-new-password-1";
    static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    static final String USERS = "(select id from app_user where email like '%@users.it')";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Warehouses warehouses;
    @Autowired JavaMailSender sender;

    @BeforeEach
    void setUp() {
        clean();
        MailSupport.stub(sender);
    }

    @AfterEach
    void clean() {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        jdbc.update("delete from one_time_token where created_by in " + USERS
                + " or (subject_type = 'USER' and subject_id in " + USERS + ")");
        jdbc.update("delete from user_warehouse where user_id in " + USERS);
        jdbc.update("update app_setting set updated_by = null where updated_by in " + USERS);
        jdbc.update("delete from app_user where email like '%@users.it'");
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

    private static RequestPostProcessor as(AppUser u) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(u.id, u.email, "Ana Cruz", u.role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.role))));
    }

    private String inviteBody(String email, String role, String warehouseIds) {
        return "{\"email\":\"" + email + "\",\"firstName\":\"New\",\"lastName\":\"Person\",\"role\":\"" + role
                + "\",\"warehouseIds\":" + warehouseIds + "}";
    }

    private org.springframework.test.web.servlet.ResultActions invite(AppUser admin, String body) throws Exception {
        return mvc.perform(post("/api/admin/users").with(as(admin)).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions accept(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/invites/accept").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private Cookie login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("TCJSESSION");
    }

    private long warehouse() {
        return warehouses.create(new WarehouseRequest("W" + UUID.randomUUID().toString().substring(0, 8), "Test WH",
                null, false, null)).id();
    }

    private String inviteStatus(String email) {
        return jdbc.queryForObject("select invite_email_status from app_user where email = ?", String.class, email);
    }

    @Test
    void inviteEmailsLinkAndAcceptSetsPassword() throws Exception {
        AppUser admin = user("admin1@users.it", Role.ADMIN);
        invite(admin, inviteBody("Newbie@Users.it", "SALES", "[]")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newbie@users.it"))
                .andExpect(jsonPath("$.invitePending").value(true))
                .andExpect(jsonPath("$.role").value("SALES"));

        List<MimeMessage> mails = MailSupport.sent(sender);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).getAllRecipients()[0].toString()).isEqualTo("newbie@users.it");
        assertThat(MailSupport.link(mails.get(0))).startsWith("http://app.test/invite/");
        assertThat(inviteStatus("newbie@users.it")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'INVITE_SENT' "
                + "and entity_id = (select id from app_user where email = 'newbie@users.it')", Integer.class))
                .isEqualTo(1);

        accept(MailSupport.token(mails.get(0)), NEW_PW).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newbie@users.it\",\"password\":\"" + NEW_PW + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.next").value("DONE"));
    }

    @Test
    void inviteAcceptReusedTokenGone() throws Exception {
        invite(user("admin2@users.it", Role.ADMIN), inviteBody("reuse@users.it", "SALES", "[]"))
                .andExpect(status().isCreated());
        String token = MailSupport.token(MailSupport.sent(sender).get(0));
        accept(token, NEW_PW).andExpect(status().isNoContent());
        accept(token, "another-password-22").andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        accept("garbage", NEW_PW).andExpect(status().isGone());
    }

    @Test
    void weakPasswordRejectedAndTokenStillUsable() throws Exception {
        invite(user("admin3@users.it", Role.ADMIN), inviteBody("weak@users.it", "SALES", "[]"))
                .andExpect(status().isCreated());
        String token = MailSupport.token(MailSupport.sent(sender).get(0));
        accept(token, "short").andExpect(status().isBadRequest());
        accept(token, "weak@users.it").andExpect(status().isBadRequest());
        accept(token, NEW_PW).andExpect(status().isNoContent());
    }

    @Test
    void warehouseRoleNeedsWarehouses() throws Exception {
        AppUser admin = user("admin4@users.it", Role.ADMIN);
        invite(admin, inviteBody("wh1@users.it", "WAREHOUSE", "[]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        invite(admin, inviteBody("wh2@users.it", "WAREHOUSE", "[999999]")).andExpect(status().isBadRequest());
        long wh = warehouse();
        invite(admin, inviteBody("wh3@users.it", "WAREHOUSE", "[" + wh + "]")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseIds[0]").value(wh));
    }

    @Test
    void duplicateActiveEmailConflictsButArchivedMayBeReinvited() throws Exception {
        AppUser admin = user("admin5@users.it", Role.ADMIN);
        user("dupe@users.it", Role.SALES);
        invite(admin, inviteBody("dupe@users.it", "SALES", "[]")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
        jdbc.update("update app_user set archived_at = now() where email = 'dupe@users.it'");
        invite(admin, inviteBody("dupe@users.it", "SALES", "[]")).andExpect(status().isCreated());
    }

    @Test
    @Transactional
    void cannotDemoteLastAdmin() throws Exception {
        AppUser admin = user("admin6@users.it", Role.ADMIN);
        onlyAdmin(admin);
        mvc.perform(put("/api/admin/users/" + admin.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SALES\",\"version\":" + admin.version + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    @Transactional
    void cannotArchiveSelfAsLastAdmin() throws Exception {
        AppUser admin = user("admin7@users.it", Role.ADMIN);
        onlyAdmin(admin);
        mvc.perform(post("/api/admin/users/" + admin.id + "/archive").with(as(admin)).with(csrf(mvc)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    @Transactional
    void canDemoteAdminWhenAnotherRemains() throws Exception {
        AppUser admin = user("admin8@users.it", Role.ADMIN);
        AppUser other = user("admin9@users.it", Role.ADMIN);
        onlyAdmins(admin, other);
        mvc.perform(put("/api/admin/users/" + other.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SALES\",\"version\":" + other.version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SALES"));
    }

    /** Other tests may leave admins behind; archive them inside this test's rolled-back transaction. */
    private void onlyAdmin(AppUser keep) {
        onlyAdmins(keep);
    }

    private void onlyAdmins(AppUser... keep) {
        String ids = String.join(",", java.util.Arrays.stream(keep).map(u -> String.valueOf(u.id)).toList());
        jdbc.update("update app_user set archived_at = now() where role = 'ADMIN' and archived_at is null "
                + "and id not in (" + ids + ")");
    }

    /** Real commits and two real threads: the row locks must serialise the guard, with no deadlock or 500. */
    @Test
    void concurrentMutualDemotionLeavesExactlyOneAdmin() throws Exception {
        AppUser a = user("race-a@users.it", Role.ADMIN);
        AppUser b = user("race-b@users.it", Role.ADMIN);
        List<Long> others = jdbc.queryForList("select id from app_user where role = 'ADMIN' and archived_at is null "
                + "and id not in (?, ?)", Long.class, a.id, b.id);
        others.forEach(id -> jdbc.update("update app_user set archived_at = now() where id = ?", id));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (AppUser[] pair : new AppUser[][]{{a, b}, {b, a}}) {
                results.add(pool.submit(() -> {
                    var request = put("/api/admin/users/" + pair[1].id).with(as(pair[0])).with(csrf(mvc))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"SALES\",\"version\":" + pair[1].version + "}");
                    ready.countDown();
                    go.await();
                    return mvc.perform(request).andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN' "
                    + "and archived_at is null and id in (?, ?)", Integer.class, a.id, b.id)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            others.forEach(id -> jdbc.update("update app_user set archived_at = null where id = ?", id));
        }
    }

    @Test
    void blankNamesRejectedOnUpdate() throws Exception {
        AppUser admin = user("admin16@users.it", Role.ADMIN);
        AppUser sales = user("sales5@users.it", Role.SALES);
        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"  \",\"version\":" + sales.version + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"\",\"version\":" + sales.version + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0917\",\"version\":" + sales.version + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void archivedUserLosesSessionImmediately() throws Exception {
        AppUser admin = user("admin10@users.it", Role.ADMIN);
        AppUser sales = user("sales1@users.it", Role.SALES);
        Cookie session = login(sales.email, PW);
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + sales.id + "/archive").with(as(admin)).with(csrf(mvc)))
                .andExpect(status().isNoContent());
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ARCHIVED' "
                + "and entity_type = 'USER' and entity_id = ?", Integer.class, sales.id)).isEqualTo(1);
    }

    @Test
    void roleChangeRevokesSessions() throws Exception {
        AppUser admin = user("admin11@users.it", Role.ADMIN);
        AppUser sales = user("sales2@users.it", Role.SALES);
        Cookie session = login(sales.email, PW);

        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"version\":" + sales.version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ROLE_CHANGED' "
                + "and entity_id = ?", Integer.class, sales.id)).isEqualTo(1);
    }

    @Test
    void assignmentChangeRevokesSessionsAndStaleVersionRejected() throws Exception {
        AppUser admin = user("admin12@users.it", Role.ADMIN);
        AppUser sales = user("sales3@users.it", Role.SALES);
        Cookie session = login(sales.email, PW);
        long wh = warehouse();

        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseIds\":[" + wh + "],\"version\":" + (sales.version + 5) + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());

        mvc.perform(put("/api/admin/users/" + sales.id).with(as(admin)).with(csrf(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseIds\":[" + wh + "],\"version\":" + sales.version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.warehouseIds[0]").value(wh));
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ASSIGNMENT_CHANGED' "
                + "and entity_id = ?", Integer.class, sales.id)).isEqualTo(1);
    }

    @Test
    void salesCannotInvite() throws Exception {
        AppUser sales = user("sales4@users.it", Role.SALES);
        invite(sales, inviteBody("nope@users.it", "SALES", "[]")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(as(sales))).andExpect(status().isForbidden());
    }

    @Test
    void failedInviteEmailMarkedFailed() throws Exception {
        doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));
        invite(user("admin13@users.it", Role.ADMIN), inviteBody("fail@users.it", "SALES", "[]"))
                .andExpect(status().isCreated());
        assertThat(inviteStatus("fail@users.it")).isEqualTo("FAILED");
    }

    @Test
    void resendInviteReplacesTokenAndOnlyForPending() throws Exception {
        AppUser admin = user("admin14@users.it", Role.ADMIN);
        invite(admin, inviteBody("resend@users.it", "SALES", "[]")).andExpect(status().isCreated());
        long id = jdbc.queryForObject("select id from app_user where email = 'resend@users.it'", Long.class);

        mvc.perform(post("/api/admin/users/" + id + "/resend-invite").with(as(admin)).with(csrf(mvc)))
                .andExpect(status().isNoContent());
        List<MimeMessage> mails = MailSupport.sent(sender);
        assertThat(mails).hasSize(2);
        accept(MailSupport.token(mails.get(0)), NEW_PW).andExpect(status().isGone());
        accept(MailSupport.token(mails.get(1)), NEW_PW).andExpect(status().isNoContent());

        mvc.perform(post("/api/admin/users/" + id + "/resend-invite").with(as(admin)).with(csrf(mvc)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByQueryRoleAndArchived() throws Exception {
        AppUser admin = user("admin15@users.it", Role.ADMIN);
        user("findme@users.it", Role.SALES);
        AppUser gone = user("gone@users.it", Role.SALES);
        jdbc.update("update app_user set archived_at = now() where id = ?", gone.id);

        mvc.perform(get("/api/admin/users").param("q", "FINDME@").param("size", "50").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("findme@users.it"));
        mvc.perform(get("/api/admin/users").param("q", "@users.it").param("role", "ADMIN").with(as(admin)))
                .andExpect(jsonPath("$.content[?(@.role != 'ADMIN')]").isEmpty());
        mvc.perform(get("/api/admin/users").param("q", "gone@users.it").with(as(admin)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/users").param("q", "gone@users.it").param("includeArchived", "true")
                        .with(as(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].archivedAt").isNotEmpty());
    }
}
